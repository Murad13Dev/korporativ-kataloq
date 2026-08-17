package az.catalog.modules.notification.push;

import az.catalog.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Customer · Push bildirişləri")
@RestController
@RequestMapping("/api/customer/push")
@RequiredArgsConstructor
public class CustomerPushController {

    private final PushSubscriptionService service;

    @Operation(summary = "Bu cihazı qeydə almaq",
            description = "Brauzerin PushSubscription.toJSON() nəticəsi birbaşa göndərilə bilər.")
    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody PushDtos.SubscribeRequest request) {
        service.subscribe(SecurityUtils.requireCustomer().id(),
                request.endpoint(), request.keys().p256dh(), request.keys().auth());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Bu cihazın qeydini silmək")
    @DeleteMapping("/subscribe")
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody PushDtos.UnsubscribeRequest request) {
        service.unsubscribe(request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
