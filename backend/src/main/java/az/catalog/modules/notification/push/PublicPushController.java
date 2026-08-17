package az.catalog.modules.notification.push;

import az.catalog.common.dto.ApiResponses.DataResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Public · Push")
@RestController
@RequestMapping("/api/public/push")
@RequiredArgsConstructor
@SecurityRequirements
public class PublicPushController {

    private final VapidKeyService vapidKeyService;

    @Operation(summary = "VAPID açıq açarı",
            description = "Brauzerin pushManager.subscribe() çağırışında applicationServerKey kimi istifadə edilir.")
    @GetMapping("/vapid-public-key")
    public DataResponse<String> vapidPublicKey() {
        return DataResponse.of(vapidKeyService.getPublicKey());
    }
}
