package az.catalog.modules.chat;

import az.catalog.common.config.AppProperties;
import az.catalog.common.config.WebSocketConfig;
import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.common.security.SecurityUtils;
import az.catalog.common.util.HtmlSanitizer;
import az.catalog.modules.commerce.customer.Customer;
import az.catalog.modules.commerce.customer.CustomerRepository;
import az.catalog.modules.content.FaqRepository;
import az.catalog.modules.media.MediaService;
import az.catalog.modules.siteconfig.SiteConfigurationService;
import az.catalog.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Live chat.
 *
 * <p>Two rules shape everything here:
 *
 * <ol>
 *   <li><b>The visitor token is the capability.</b> Every visitor-facing method is scoped
 *       by it, so an unauthenticated endpoint cannot reach someone else's thread.</li>
 *   <li><b>Broadcasts happen after commit.</b> Publishing inside the transaction would let
 *       a subscriber receive a message that a subsequent rollback erases — the client
 *       would show something the database never kept.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int PREVIEW_LENGTH = 160;
    /** Below this trigram similarity, a "matching" FAQ is really just noise. */
    private static final double FAQ_MATCH_THRESHOLD = 0.3;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final QuickReplyRepository quickReplyRepository;
    private final FaqRepository faqRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final MediaService mediaService;
    private final SimpMessagingTemplate messaging;
    private final SiteConfigurationService siteConfigurationService;
    private final HtmlSanitizer sanitizer;
    private final AppProperties properties;

    // =======================================================================
    // Visitor
    // =======================================================================

    /**
     * Opens a thread, or returns the visitor's existing open one. Re-opening the widget
     * must never start a second conversation — the operator would see two half-threads.
     */
    @Transactional
    public ChatDtos.ConversationView start(String visitorToken, ChatDtos.StartRequest request) {
        requireChatEnabled();
        requireToken(visitorToken);

        Optional<Conversation> existing = conversationRepository
                .findOpenByVisitorToken(visitorToken).stream().findFirst();

        if (existing.isPresent()) {
            Conversation conversation = existing.get();
            if (request != null && request.message() != null && !request.message().isBlank()) {
                appendCustomerMessage(conversation, request.message(), null);
            }
            return toView(conversation);
        }

        Customer customer = SecurityUtils.currentCustomerId()
                .flatMap(customerRepository::findAliveById)
                .orElse(null);

        Conversation conversation = conversationRepository.save(Conversation.builder()
                .visitorToken(visitorToken)
                .customer(customer)
                .visitorName(resolveName(request, customer))
                .visitorEmail(resolveEmail(request, customer))
                .visitorPhone(request == null ? null : request.phone())
                .subject(request == null ? null : sanitizer.stripTags(request.subject()))
                .productId(request == null ? null : request.productId())
                .orderId(request == null ? null : request.orderId())
                .sourceUrl(request == null ? null : request.sourceUrl())
                .status(Conversation.Status.OPEN)
                .build());

        // A greeting gives the visitor something to read while nobody has replied yet.
        appendMessage(conversation, Message.SenderType.SYSTEM, null, null,
                "Salam! Sualınızı yazın, komandamız qısa zamanda cavablandıracaq.",
                Message.MessageType.SYSTEM_EVENT, null, false);

        if (request != null && request.message() != null && !request.message().isBlank()) {
            appendCustomerMessage(conversation, request.message(), null);
            suggestFaqAnswer(conversation, request.message());
        }

        log.info("Opened conversation {} for {}", conversation.getId(), conversation.displayName());
        return toView(conversation);
    }

    @Transactional(readOnly = true)
    public Optional<ChatDtos.ConversationView> currentForVisitor(String visitorToken) {
        if (visitorToken == null || visitorToken.isBlank()) {
            return Optional.empty();
        }
        return conversationRepository.findOpenByVisitorToken(visitorToken).stream()
                .findFirst()
                .map(this::toView);
    }

    @Transactional
    public ChatDtos.MessageDto sendFromVisitor(String visitorToken, Long conversationId,
                                               ChatDtos.SendMessageRequest request) {
        requireChatEnabled();
        Conversation conversation = requireVisitorConversation(visitorToken, conversationId);

        if (conversation.isClosed()) {
            throw new ApiException(ErrorCode.CONVERSATION_CLOSED);
        }
        return appendCustomerMessage(conversation, request.message(), request.mediaId());
    }

    /** Visitor read the operator's replies. */
    @Transactional
    public void markReadByVisitor(String visitorToken, Long conversationId) {
        Conversation conversation = requireVisitorConversation(visitorToken, conversationId);
        messageRepository.markRead(conversationId, Message.SenderType.ADMIN, OffsetDateTime.now());
        conversation.setUnreadByCustomer(0);
    }

    // =======================================================================
    // Admin
    // =======================================================================

    @Transactional(readOnly = true)
    public Page<ChatDtos.InboxRow> inbox(String search, Conversation.Status status,
                                         Long assignedAdminId, boolean unreadOnly,
                                         Pageable pageable) {
        return conversationRepository
                .inbox(blankToNull(search), status, assignedAdminId, unreadOnly, pageable)
                .map(this::toInboxRow);
    }

    @Transactional(readOnly = true)
    public ChatDtos.ConversationDetail detail(Long conversationId) {
        Conversation conversation = loadConversation(conversationId);

        return new ChatDtos.ConversationDetail(
                conversation.getId(), conversation.getVisitorToken(),
                conversation.displayName(), conversation.contactEmail(),
                conversation.getVisitorPhone(), conversation.getSubject(),
                conversation.getStatus(), conversation.getStatus().label(),
                conversation.getPriority(),
                conversation.getCustomer() == null ? null : conversation.getCustomer().getId(),
                conversation.getAssignedAdminId(), adminName(conversation.getAssignedAdminId()),
                conversation.getProductId(), conversation.getOrderId(),
                conversation.getSourceUrl(), conversation.getLocale(),
                messages(conversationId),
                conversation.getCreatedAt(), conversation.getClosedAt());
    }

    @Transactional
    public ChatDtos.MessageDto replyAsAdmin(Long conversationId, ChatDtos.SendMessageRequest request) {
        Conversation conversation = loadConversation(conversationId);

        if (conversation.isClosed()) {
            throw new ApiException(ErrorCode.CONVERSATION_CLOSED,
                    "Bağlanmış söhbətə cavab yazmaq üçün onu yenidən açın");
        }

        var principal = SecurityUtils.requireAdmin();

        // Replying claims an unassigned thread: the person answering is the owner.
        if (conversation.getAssignedAdminId() == null) {
            conversation.setAssignedAdminId(principal.id());
        }
        if (conversation.getStatus() == Conversation.Status.PENDING) {
            conversation.setStatus(Conversation.Status.OPEN);
        }

        ChatDtos.MessageDto dto = appendMessage(conversation, Message.SenderType.ADMIN,
                principal.id(), principal.displayName(),
                request.message(), Message.MessageType.TEXT, request.mediaId(), true);

        // The operator has necessarily seen everything above their own reply.
        messageRepository.markRead(conversationId, Message.SenderType.CUSTOMER, OffsetDateTime.now());
        conversation.setUnreadByAdmin(0);

        return dto;
    }

    @Transactional
    public void markReadByAdmin(Long conversationId) {
        Conversation conversation = loadConversation(conversationId);
        messageRepository.markRead(conversationId, Message.SenderType.CUSTOMER, OffsetDateTime.now());
        conversation.setUnreadByAdmin(0);
        broadcastInboxUpdate(conversation);
    }

    @Transactional
    public ChatDtos.ConversationDetail assign(Long conversationId, Long adminId) {
        Conversation conversation = loadConversation(conversationId);

        if (adminId != null) {
            userRepository.findAliveById(adminId)
                    .orElseThrow(() -> ApiException.notFound("İstifadəçi", adminId));
        }
        conversation.setAssignedAdminId(adminId);

        appendMessage(conversation, Message.SenderType.SYSTEM, null, null,
                adminId == null
                        ? "Söhbətin təyinatı ləğv edildi"
                        : "Söhbət %s-ə təyin edildi".formatted(adminName(adminId)),
                Message.MessageType.SYSTEM_EVENT, null, false);

        return detail(conversationId);
    }

    @Transactional
    public ChatDtos.ConversationDetail changeStatus(Long conversationId, Conversation.Status status) {
        Conversation conversation = loadConversation(conversationId);
        Conversation.Status previous = conversation.getStatus();
        conversation.setStatus(status);

        if (status == Conversation.Status.CLOSED) {
            conversation.setClosedAt(OffsetDateTime.now());
            conversation.setClosedBy(SecurityUtils.currentAdminId().orElse(null));
        } else if (previous == Conversation.Status.CLOSED) {
            // Re-opening clears the closure so the thread does not look both open and closed.
            conversation.setClosedAt(null);
            conversation.setClosedBy(null);
        }

        appendMessage(conversation, Message.SenderType.SYSTEM, null, null,
                "Söhbətin statusu dəyişdi: " + status.label(),
                Message.MessageType.SYSTEM_EVENT, null, false);

        return detail(conversationId);
    }

    @Transactional
    public ChatDtos.ConversationDetail changePriority(Long conversationId,
                                                      Conversation.Priority priority) {
        loadConversation(conversationId).setPriority(priority);
        return detail(conversationId);
    }

    @Transactional(readOnly = true)
    public List<ChatDtos.QuickReplyDto> quickReplies(String language) {
        return quickReplyRepository.findActive(language).stream()
                .map(reply -> new ChatDtos.QuickReplyDto(
                        reply.getId(), reply.getCode(), reply.getTitle(), reply.getBody()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatDtos.UnreadSummary unreadSummary() {
        return new ChatDtos.UnreadSummary(
                conversationRepository.totalUnreadForAdmin(),
                conversationRepository.countOpen());
    }

    // =======================================================================
    // Internals
    // =======================================================================

    private ChatDtos.MessageDto appendCustomerMessage(Conversation conversation, String text,
                                                      Long mediaId) {
        Customer customer = conversation.getCustomer();
        return appendMessage(conversation, Message.SenderType.CUSTOMER,
                customer == null ? null : customer.getId(),
                conversation.displayName(), text, Message.MessageType.TEXT, mediaId, true);
    }

    /**
     * Only fires on the very first message of a thread — see the class javadoc on why an
     * auto-reply belongs at "I have a question" rather than repeated on every follow-up,
     * where it would just talk over the human conversation already under way. A miss is
     * silent: no bot message beats a wrong one.
     */
    private void suggestFaqAnswer(Conversation conversation, String customerMessage) {
        faqRepository.findBestMatchingAnswer(customerMessage, conversation.getLocale(), FAQ_MATCH_THRESHOLD)
                .ifPresent(answer -> appendMessage(conversation, Message.SenderType.BOT, null,
                        "Virtual Asistan", answer, Message.MessageType.TEXT, null, false));
    }

    /**
     * Single write path for every message, so the denormalised counters and the two
     * broadcasts can never be forgotten at a call site.
     */
    private ChatDtos.MessageDto appendMessage(Conversation conversation,
                                              Message.SenderType senderType,
                                              Long senderId, String senderName,
                                              String text, Message.MessageType type,
                                              Long mediaId, boolean countsAsUnread) {

        // Chat text is rendered as plain text client-side, but it is stripped here too:
        // a transcript may later be pasted into an e-mail that does render HTML.
        String clean = sanitizer.stripTags(text);
        if ((clean == null || clean.isBlank()) && mediaId == null) {
            throw ApiException.badRequest("Mesaj boş ola bilməz");
        }

        Message message = messageRepository.save(Message.builder()
                .conversationId(conversation.getId())
                .senderType(senderType)
                .senderId(senderId)
                .senderName(senderName)
                .message(clean)
                .messageType(type)
                .mediaId(mediaId)
                .read(!countsAsUnread)
                .createdAt(OffsetDateTime.now())
                .build());

        conversation.setLastMessageAt(message.getCreatedAt());
        conversation.setLastMessagePreview(preview(clean, type));

        if (countsAsUnread) {
            if (senderType == Message.SenderType.CUSTOMER) {
                conversation.setUnreadByAdmin(conversation.getUnreadByAdmin() + 1);
                if (conversation.getStatus() == Conversation.Status.RESOLVED) {
                    // A follow-up question re-opens a thread someone had closed off.
                    conversation.setStatus(Conversation.Status.OPEN);
                }
            } else if (senderType == Message.SenderType.ADMIN) {
                conversation.setUnreadByCustomer(conversation.getUnreadByCustomer() + 1);
            }
        }

        ChatDtos.MessageDto dto = toDto(message);
        broadcastAfterCommit(conversation, dto);
        return dto;
    }

    /**
     * Publishes to the visitor's channel and the admin inbox once the transaction
     * commits. See the class javadoc for why this cannot happen inline.
     */
    private void broadcastAfterCommit(Conversation conversation, ChatDtos.MessageDto message) {
        String channel = WebSocketConfig.TOPIC_CONVERSATION + conversation.getVisitorToken();
        ChatDtos.InboxRow row = toInboxRow(conversation);

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(channel, message, row);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(channel, message, row);
            }
        });
    }

    private void publish(String channel, ChatDtos.MessageDto message, ChatDtos.InboxRow row) {
        try {
            messaging.convertAndSend(channel, message);
            messaging.convertAndSend(WebSocketConfig.TOPIC_ADMIN_INBOX,
                    ChatDtos.InboxEvent.newMessage(row, message));
        } catch (RuntimeException ex) {
            // A broker hiccup must not fail the request: the message is already stored
            // and the client polls history on reconnect.
            log.error("Failed to broadcast chat message {}", message.id(), ex);
        }
    }

    private void broadcastInboxUpdate(Conversation conversation) {
        ChatDtos.InboxRow row = toInboxRow(conversation);
        try {
            messaging.convertAndSend(WebSocketConfig.TOPIC_ADMIN_INBOX, ChatDtos.InboxEvent.updated(row));
        } catch (RuntimeException ex) {
            log.debug("Inbox update broadcast failed: {}", ex.getMessage());
        }
    }

    private List<ChatDtos.MessageDto> messages(Long conversationId) {
        return messageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(this::toDto)
                .toList();
    }

    private ChatDtos.MessageDto toDto(Message message) {
        return new ChatDtos.MessageDto(
                message.getId(), message.getConversationId(), message.getSenderType(),
                message.getSenderId(), message.getSenderName(), message.getMessage(),
                message.getMessageType(), mediaService.publicUrl(message.getMediaId()),
                message.getMetadata(), message.isRead(), message.getCreatedAt());
    }

    private ChatDtos.ConversationView toView(Conversation conversation) {
        return new ChatDtos.ConversationView(
                conversation.getId(), conversation.getVisitorToken(),
                conversation.getStatus(), conversation.getStatus().label(),
                conversation.getSubject(),
                conversation.getAssignedAdminId() != null,
                adminName(conversation.getAssignedAdminId()),
                conversation.getUnreadByCustomer(),
                messages(conversation.getId()),
                conversation.getCreatedAt());
    }

    private ChatDtos.InboxRow toInboxRow(Conversation conversation) {
        return new ChatDtos.InboxRow(
                conversation.getId(), conversation.displayName(), conversation.contactEmail(),
                conversation.getSubject(), conversation.getStatus(),
                conversation.getStatus().label(), conversation.getPriority(),
                conversation.getAssignedAdminId(), adminName(conversation.getAssignedAdminId()),
                conversation.getLastMessagePreview(), conversation.getLastMessageAt(),
                conversation.getUnreadByAdmin(), conversation.getCustomer() != null,
                conversation.getCreatedAt());
    }

    private String preview(String text, Message.MessageType type) {
        if (type == Message.MessageType.IMAGE) {
            return "📷 Şəkil";
        }
        if (type == Message.MessageType.FILE) {
            return "📎 Fayl";
        }
        if (text == null) {
            return null;
        }
        return text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH - 1) + "…" : text;
    }

    private String adminName(Long adminId) {
        return adminId == null ? null
                : userRepository.findAliveById(adminId).map(user -> user.fullName()).orElse(null);
    }

    private String resolveName(ChatDtos.StartRequest request, Customer customer) {
        if (request != null && request.name() != null && !request.name().isBlank()) {
            return sanitizer.stripTags(request.name()).trim();
        }
        return customer == null ? null : customer.fullName();
    }

    private String resolveEmail(ChatDtos.StartRequest request, Customer customer) {
        if (request != null && request.email() != null && !request.email().isBlank()) {
            return request.email().trim().toLowerCase();
        }
        return customer == null ? null : customer.getEmail();
    }

    private Conversation requireVisitorConversation(String visitorToken, Long conversationId) {
        requireToken(visitorToken);
        return conversationRepository.findByIdAndVisitorToken(conversationId, visitorToken)
                .orElseThrow(() -> ApiException.notFound("Söhbət", conversationId));
    }

    private void requireToken(String visitorToken) {
        // 32 characters is below a UUID's length; anything shorter is not a credential.
        if (visitorToken == null || visitorToken.length() < 32) {
            throw ApiException.badRequest("Etibarlı ziyarətçi tokeni tələb olunur");
        }
    }

    private void requireChatEnabled() {
        if (!properties.getChat().isEnabled()
                || !siteConfigurationService.publishedSnapshot().chatEnabled()) {
            throw new ApiException(ErrorCode.CHAT_DISABLED);
        }
    }

    private Conversation loadConversation(Long id) {
        return conversationRepository.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Söhbət", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Closes threads nobody has touched for the configured window. Runs hourly. */
    @Scheduled(cron = "0 20 * * * *")
    @Transactional
    public void closeStaleConversations() {
        OffsetDateTime cutoff = OffsetDateTime.now()
                .minusHours(properties.getChat().getAutoCloseAfterHours());

        List<Conversation> stale = conversationRepository.findStale(cutoff);
        for (Conversation conversation : stale) {
            conversation.setStatus(Conversation.Status.CLOSED);
            conversation.setClosedAt(OffsetDateTime.now());
        }
        if (!stale.isEmpty()) {
            log.info("Auto-closed {} idle conversations", stale.size());
        }
    }
}
