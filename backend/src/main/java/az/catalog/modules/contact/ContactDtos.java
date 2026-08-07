package az.catalog.modules.contact;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class ContactDtos {

    private ContactDtos() {
    }

    // -----------------------------------------------------------------------
    // Public
    // -----------------------------------------------------------------------

    public record SubmitRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 160) String companyName,
            @NotBlank @Email @Size(max = 190) String email,
            @Size(max = 40) String phone,
            @Size(max = 190) String subject,
            @NotBlank @Size(max = 4000) String message,
            ContactRequest.RequestType requestType,
            @Size(max = 500) String sourceUrl,

            @AssertTrue(message = "Məxfilik siyasəti qəbul edilməlidir")
            boolean privacyAccepted,

            /**
             * Honeypot. A real form leaves it empty because the field is hidden;
             * most bots fill every input they find.
             */
            @Size(max = 200) String website
    ) {
    }

    /** Product-page inquiry: the product comes from the path, not the body. */
    public record InquiryRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 160) String companyName,
            @NotBlank @Email @Size(max = 190) String email,
            @Size(max = 40) String phone,
            @NotBlank @Size(max = 4000) String message,
            @Size(max = 500) String sourceUrl,
            @AssertTrue(message = "Məxfilik siyasəti qəbul edilməlidir")
            boolean privacyAccepted,
            @Size(max = 200) String website
    ) {
    }

    public record NewsletterRequest(
            @NotBlank @Email @Size(max = 190) String email,
            @Size(max = 160) String name,
            @Size(max = 200) String website
    ) {
    }

    // -----------------------------------------------------------------------
    // Admin
    // -----------------------------------------------------------------------

    public record RequestRow(
            Long id,
            String name,
            String companyName,
            String email,
            String phone,
            String subject,
            ContactRequest.RequestType requestType,
            String requestTypeLabel,
            ContactRequest.Status status,
            String statusLabel,
            Long productId,
            String productName,
            OffsetDateTime createdAt
    ) {
    }

    public record RequestDetail(
            Long id,
            String name,
            String companyName,
            String email,
            String phone,
            String subject,
            String message,
            ContactRequest.RequestType requestType,
            String requestTypeLabel,
            ContactRequest.Status status,
            String statusLabel,
            Long productId,
            String productName,
            String productSlug,
            String adminNote,
            Long handledBy,
            String handledByName,
            OffsetDateTime handledAt,
            String sourceUrl,
            String ipAddress,
            OffsetDateTime createdAt
    ) {
    }

    public record StatusRequest(@NotNull ContactRequest.Status status) {
    }

    public record NoteRequest(@Size(max = 4000) String note) {
    }

    public record Counters(
            long total,
            long newCount,
            long inProgress,
            long answered,
            long spam
    ) {
    }

    public record SubscriberRow(
            Long id,
            String email,
            String name,
            NewsletterSubscription.Status status,
            OffsetDateTime confirmedAt,
            OffsetDateTime createdAt
    ) {
    }
}
