package az.catalog.modules.chat;

import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.ratelimit.RateLimiterService;
import az.catalog.common.util.RequestUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@Tag(name = "Public · Canlı dəstək",
        description = """
                Ziyarətçi `X-Visitor-Token` başlığında brauzerdə saxlanan gizli token göndərir.
                Bu token həm şəxsiyyət, həm də STOMP kanalının açarıdır:
                `/topic/chat/{token}`.
                """)
@RestController
@RequestMapping("/api/public/chat")
@RequiredArgsConstructor
@SecurityRequirements
public class PublicChatController {

    private static final String TOKEN_HEADER = "X-Visitor-Token";

    private final ChatService chatService;
    private final RateLimiterService rateLimiter;
    private final az.catalog.common.config.AppProperties properties;
    private final az.catalog.modules.siteconfig.SiteConfigurationService siteConfiguration;

    @Operation(summary = "Söhbəti başlatmaq və ya davam etdirmək",
            description = "Açıq söhbət varsa yenisi yaradılmır — mövcud olan qaytarılır.")
    @PostMapping
    public DataResponse<ChatDtos.ConversationView> start(
            @Parameter(description = "Brauzerdə saxlanan gizli token")
            @RequestHeader(TOKEN_HEADER) String visitorToken,
            @Valid @RequestBody(required = false) ChatDtos.StartRequest request,
            HttpServletRequest httpRequest) {

        rateLimiter.consume("chat-start:" + RequestUtils.clientIp(httpRequest), 5, Duration.ofMinutes(10));
        return DataResponse.of(chatService.start(visitorToken, request));
    }

    @Operation(summary = "Cari söhbət",
            description = "Widget açılanda çağırılır. Açıq söhbət yoxdursa `null` qaytarır.")
    @GetMapping
    public DataResponse<ChatDtos.ConversationView> current(
            @RequestHeader(value = TOKEN_HEADER, required = false) String visitorToken) {
        return DataResponse.of(chatService.currentForVisitor(visitorToken).orElse(null));
    }

    @Operation(summary = "Mesaj göndərmək")
    @PostMapping("/{conversationId}/messages")
    public DataResponse<ChatDtos.MessageDto> send(
            @RequestHeader(TOKEN_HEADER) String visitorToken,
            @PathVariable Long conversationId,
            @Valid @RequestBody ChatDtos.SendMessageRequest request,
            HttpServletRequest httpRequest) {

        // Generous enough for a real conversation, tight enough to stop a script.
        rateLimiter.consume("chat-send:" + RequestUtils.clientIp(httpRequest), 30, Duration.ofMinutes(1));
        return DataResponse.of(chatService.sendFromVisitor(visitorToken, conversationId, request));
    }

    @Operation(summary = "Oxundu kimi işarələmək")
    @PostMapping("/{conversationId}/read")
    public ResponseEntity<Void> markRead(
            @RequestHeader(TOKEN_HEADER) String visitorToken,
            @PathVariable Long conversationId) {
        chatService.markReadByVisitor(visitorToken, conversationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Chat aktivdirmi",
            description = "Widget yüklənməzdən əvvəl yoxlanılır. Həm server, həm sayt konfiqurasiyası nəzərə alınır.")
    @GetMapping("/availability")
    public DataResponse<Map<String, Boolean>> availability() {
        boolean enabled = properties.getChat().isEnabled()
                && siteConfiguration.publishedSnapshot().chatEnabled();
        return DataResponse.of(Map.of("enabled", enabled));
    }
}
