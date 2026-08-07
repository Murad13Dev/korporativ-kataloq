package az.catalog.modules.commerce.quote;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

public final class QuoteDtos {

    private QuoteDtos() {
    }

    public record ItemRequest(
            @NotNull Long productId,
            @Min(1) int quantity
    ) {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 160) String contactName,
            @NotBlank @Email @Size(max = 190) String contactEmail,
            @Size(max = 40) String contactPhone,
            @Size(max = 2000) String note,
            @NotEmpty List<@Valid ItemRequest> items
    ) {
    }

    public record ItemView(
            Long id,
            Long productId,
            String productName,
            String productSlug,
            int quantity,
            BigDecimal quotedUnitPrice,
            BigDecimal lineTotal
    ) {
    }

    public record QuoteView(
            Long id,
            String contactName,
            String contactEmail,
            String contactPhone,
            String note,
            QuoteStatus status,
            String adminNote,
            Instant validUntil,
            BigDecimal quotedTotal,
            String currency,
            List<ItemView> items,
            OffsetDateTime createdAt
    ) {
    }

    public record QuoteRow(
            Long id,
            String contactName,
            String contactEmail,
            QuoteStatus status,
            int itemCount,
            BigDecimal quotedTotal,
            String currency,
            OffsetDateTime createdAt
    ) {
    }

    public record ItemPriceInput(
            @NotNull Long itemId,
            @NotNull BigDecimal unitPrice
    ) {
    }

    public record SubmitQuoteRequest(
            @NotEmpty List<@Valid ItemPriceInput> prices,
            Instant validUntil,
            @Size(max = 2000) String adminNote
    ) {
    }

    public record DeclineRequest(
            @Size(max = 2000) String adminNote
    ) {
    }
}
