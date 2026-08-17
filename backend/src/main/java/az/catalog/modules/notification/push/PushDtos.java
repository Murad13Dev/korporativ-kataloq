package az.catalog.modules.notification.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public final class PushDtos {

    private PushDtos() {
    }

    /** Matches the browser's own {@code PushSubscription.toJSON()} shape exactly, so the
        frontend can post that object straight through with no reshaping. */
    public record SubscribeRequest(
            @NotBlank String endpoint,
            @Valid Keys keys
    ) {
        public record Keys(@NotBlank String p256dh, @NotBlank String auth) {
        }
    }

    public record UnsubscribeRequest(@NotBlank String endpoint) {
    }
}
