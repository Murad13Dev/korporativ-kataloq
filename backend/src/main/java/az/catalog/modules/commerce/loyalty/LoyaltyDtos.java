package az.catalog.modules.commerce.loyalty;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public final class LoyaltyDtos {

    private LoyaltyDtos() {
    }

    public record BalanceDto(int points, int minRedeemable, int pointsPerBlock, java.math.BigDecimal valuePerBlock) {
    }

    public record TransactionDto(int points, LoyaltyTransaction.Type type, String description, OffsetDateTime createdAt) {
        public static TransactionDto from(LoyaltyTransaction transaction) {
            return new TransactionDto(transaction.getPoints(), transaction.getType(),
                    transaction.getDescription(), transaction.getCreatedAt());
        }
    }

    public record RedeemRequest(@NotNull @Min(100) Integer points) {
    }

    public record RedeemResponse(String promoCode, java.math.BigDecimal amount, int remainingPoints) {
    }
}
