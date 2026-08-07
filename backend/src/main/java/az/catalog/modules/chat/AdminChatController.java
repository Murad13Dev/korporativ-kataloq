package az.catalog.modules.chat;

import az.catalog.common.config.RequestLanguage;
import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.dto.ApiResponses.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin · Canlı dəstək")
@RestController
@RequestMapping("/api/admin/chat")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','SUPPORT')")
public class AdminChatController {

    private final ChatService service;

    @Operation(summary = "Söhbət qutusu",
            description = "Yeni mesajlar `/topic/admin/chat` STOMP kanalından canlı gəlir.")
    @GetMapping("/conversations")
    public PageResponse<ChatDtos.InboxRow> inbox(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Conversation.Status status,
            @RequestParam(required = false) Long assignedAdminId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 25, sort = "lastMessageAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.of(service.inbox(search, status, assignedAdminId, unreadOnly, pageable));
    }

    @Operation(summary = "Söhbət detalı", description = "Bütün mesaj tarixçəsi ilə birlikdə.")
    @GetMapping("/conversations/{id}")
    public DataResponse<ChatDtos.ConversationDetail> detail(@PathVariable Long id) {
        return DataResponse.of(service.detail(id));
    }

    @Operation(summary = "Cavab yazmaq",
            description = "Təyin edilməmiş söhbət cavab yazan operatora avtomatik təyin olunur.")
    @PostMapping("/conversations/{id}/messages")
    public DataResponse<ChatDtos.MessageDto> reply(
            @PathVariable Long id, @Valid @RequestBody ChatDtos.SendMessageRequest request) {
        return DataResponse.of(service.replyAsAdmin(id, request));
    }

    @Operation(summary = "Oxundu kimi işarələmək")
    @PostMapping("/conversations/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        service.markReadByAdmin(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Operatora təyin etmək", description = "adminId null göndərilsə təyinat ləğv olunur.")
    @PatchMapping("/conversations/{id}/assign")
    public DataResponse<ChatDtos.ConversationDetail> assign(
            @PathVariable Long id, @RequestBody(required = false) ChatDtos.AssignRequest request) {
        return DataResponse.of(service.assign(id, request == null ? null : request.adminId()));
    }

    @Operation(summary = "Status dəyişmək")
    @PatchMapping("/conversations/{id}/status")
    public DataResponse<ChatDtos.ConversationDetail> changeStatus(
            @PathVariable Long id, @Valid @RequestBody ChatDtos.StatusChangeRequest request) {
        return DataResponse.of(service.changeStatus(id, request.status()));
    }

    @Operation(summary = "Prioritet dəyişmək")
    @PatchMapping("/conversations/{id}/priority")
    public DataResponse<ChatDtos.ConversationDetail> changePriority(
            @PathVariable Long id, @Valid @RequestBody ChatDtos.PriorityRequest request) {
        return DataResponse.of(service.changePriority(id, request.priority()));
    }

    @Operation(summary = "Hazır cavablar")
    @GetMapping("/quick-replies")
    public DataResponse<List<ChatDtos.QuickReplyDto>> quickReplies(@RequestLanguage String language) {
        return DataResponse.of(service.quickReplies(language));
    }

    @Operation(summary = "Oxunmamış sayı", description = "Sidebar nişanı üçün.")
    @GetMapping("/unread")
    public DataResponse<ChatDtos.UnreadSummary> unread() {
        return DataResponse.of(service.unreadSummary());
    }
}
