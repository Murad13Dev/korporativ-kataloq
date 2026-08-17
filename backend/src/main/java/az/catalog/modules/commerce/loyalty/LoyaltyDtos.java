package az.catalog.modules.commerce.loyalty;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public final class LoyaltyDtos {

    private LoyaltyDtos() {
    }

    public record BalanceDto(
            int points, int minRedeemable, int pointsPerBlock, java.math.BigDecimal valuePerBlock,
            String tierName, String nextTierName, Integer pointsToNextTier, int tierProgressPercent) {
    }

    /** One rung of the tier ladder — see {@link LoyaltyService#TIERS}. */
    public record Tier(String name, int threshold) {
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
