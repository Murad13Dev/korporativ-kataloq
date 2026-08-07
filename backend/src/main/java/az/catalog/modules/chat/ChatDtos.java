package az.catalog.modules.chat;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public final class ChatDtos {

    private ChatDtos() {
    }

    // -----------------------------------------------------------------------
    // Shared
    // -----------------------------------------------------------------------

    public record MessageDto(
            Long id,
            Long conversationId,
            Message.SenderType senderType,
            Long senderId,
            String senderName,
            String message,
            Message.MessageType messageType,
            String mediaUrl,
            JsonNode metadata,
            boolean read,
            OffsetDateTime createdAt
    ) {
    }

    /** What the visitor widget holds: the thread plus its channel key. */
    public record ConversationView(
            Long id,
            /** STOMP subscription key — /topic/chat/{channelKey}. */
            String channelKey,
            Conversation.Status status,
            String statusLabel,
            String subject,
            boolean assigned,
            String assignedAdminName,
            int unread,
            List<MessageDto> messages,
            OffsetDateTime createdAt
    ) {
    }

    // -----------------------------------------------------------------------
    // Public (visitor)
    // -----------------------------------------------------------------------

    public record StartRequest(
            @Size(max = 160) String name,
            @Email @Size(max = 190) String email,
            @Size(max = 40) String phone,
            @Size(max = 255) String subject,
            @Size(max = 4000) String message,
            /** Set when the widget is opened from a product page. */
            Long productId,
            Long orderId,
            @Size(max = 500) String sourceUrl
    ) {
    }

    public record SendMessageRequest(
            @NotBlank @Size(max = 4000) String message,
            Long mediaId
    ) {
    }

    // -----------------------------------------------------------------------
    // Admin
    // -----------------------------------------------------------------------

    public record InboxRow(
            Long id,
            String displayName,
            String contactEmail,
            String subject,
            Conversation.Status status,
            String statusLabel,
            Conversation.Priority priority,
            Long assignedAdminId,
            String assignedAdminName,
            String lastMessagePreview,
            OffsetDateTime lastMessageAt,
            int unreadByAdmin,
            boolean registeredCustomer,
            OffsetDateTime createdAt
    ) {
    }

    public record ConversationDetail(
            Long id,
            String channelKey,
            String displayName,
            String contactEmail,
            String contactPhone,
            String subject,
            Conversation.Status status,
            String statusLabel,
            Conversation.Priority priority,
            Long customerId,
            Long assignedAdminId,
            String assignedAdminName,
            Long productId,
            Long orderId,
            String sourceUrl,
            String locale,
            List<MessageDto> messages,
            OffsetDateTime createdAt,
            OffsetDateTime closedAt
    ) {
    }

    public record AssignRequest(Long adminId) {
    }

    public record StatusChangeRequest(@NotNull Conversation.Status status) {
    }

    public record PriorityRequest(@NotNull Conversation.Priority priority) {
    }

    public record QuickReplyDto(Long id, String code, String title, String body) {
    }

    /** Pushed on the admin inbox topic whenever a thread changes. */
    public record InboxEvent(
            String type,
            Long conversationId,
            InboxRow conversation,
            MessageDto message
    ) {
        public static InboxEvent newMessage(InboxRow row, MessageDto message) {
            return new InboxEvent("NEW_MESSAGE", row.id(), row, message);
        }

        public static InboxEvent updated(InboxRow row) {
            return new InboxEvent("UPDATED", row.id(), row, null);
        }
    }

    /** Lightweight badge payload for the admin sidebar. */
    public record UnreadSummary(long unreadMessages, long openConversations) {
    }
}
