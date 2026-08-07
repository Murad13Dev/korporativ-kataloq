package az.catalog.modules.commerce.promo;

import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Validation and redemption of cart-level vouchers.
 *
 * Validation is deliberately split from redemption: the cart page validates on every
 * read (so an expired code drops off by itself), while redemption happens exactly once,
 * inside the order transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromoCodeService {

    private final PromoCodeRepository repository;
    private final PromoCodeRedemptionRepository redemptionRepository;

    /**
     * @param subtotal          cart subtotal after product campaigns
     * @param discountedSubtotal portion of the subtotal that is already on campaign
     * @throws ApiException when the code cannot be applied, with a specific reason code
     */
    @Transactional(readOnly = true)
    public PromoCode validate(String code, BigDecimal subtotal, BigDecimal discountedSubtotal,
                              Long customerId, String email) {
        PromoCode promo = repository.findAliveByCode(code.trim())
                .orElseThrow(() -> new ApiException(ErrorCode.PROMO_CODE_INVALID));

        OffsetDateTime now = OffsetDateTime.now();
        if (promo.getStatus() != az.catalog.common.domain.ContentStatus.ACTIVE) {
            throw new ApiException(ErrorCode.PROMO_CODE_INVALID);
        }
        if (promo.getStartsAt() != null && now.isBefore(promo.getStartsAt())) {
            throw new ApiException(ErrorCode.PROMO_CODE_INVALID, "Promo kod hələ aktiv deyil");
        }
        if (promo.getEndsAt() != null && !now.isBefore(promo.getEndsAt())) {
            throw new ApiException(ErrorCode.PROMO_CODE_EXPIRED);
        }
        if (!promo.hasCapacity()) {
            throw new ApiException(ErrorCode.PROMO_CODE_USAGE_LIMIT);
        }

        BigDecimal eligible = eligibleSubtotal(promo, subtotal, discountedSubtotal);
        if (eligible.compareTo(promo.getMinSubtotal()) < 0) {
            throw new ApiException(ErrorCode.PROMO_CODE_MIN_SUBTOTAL,
                    "Bu promo kod üçün minimum səbət məbləği %s-dir".formatted(
                            promo.getMinSubtotal().stripTrailingZeros().toPlainString()));
        }

        if ((customerId != null || email != null) && promo.getUsageLimitPerCustomer() > 0) {
            long used = redemptionRepository.countForCustomer(promo.getId(), customerId, email);
            if (used >= promo.getUsageLimitPerCustomer()) {
                throw new ApiException(ErrorCode.PROMO_CODE_USAGE_LIMIT,
                        "Bu promo kodu artıq istifadə etmisiniz");
            }
        }
        return promo;
    }

    /** Amount the voucher takes off, given the campaign-aware subtotal. */
    public BigDecimal amountFor(PromoCode promo, BigDecimal subtotal, BigDecimal discountedSubtotal) {
        return promo.discountFor(eligibleSubtotal(promo, subtotal, discountedSubtotal));
    }

    /**
     * A code flagged {@code excludeDiscounted} only applies to the part of the cart that
     * is not already on a campaign — the usual way to stop "20% off" stacking onto a
     * clearance item down to nothing.
     */
    private BigDecimal eligibleSubtotal(PromoCode promo, BigDecimal subtotal, BigDecimal discountedSubtotal) {
        if (!promo.isExcludeDiscounted()) {
            return subtotal;
        }
        BigDecimal eligible = subtotal.subtract(discountedSubtotal == null ? BigDecimal.ZERO : discountedSubtotal);
        return eligible.signum() > 0 ? eligible : BigDecimal.ZERO;
    }

    /**
     * Consumes one use. Called inside the order transaction, so a rolled-back checkout
     * never burns a voucher.
     */
    @Transactional
    public void redeem(PromoCode promo, Long orderId, Long customerId, String email, BigDecimal amount) {
        if (repository.incrementUsage(promo.getId()) == 0) {
            // Lost the race against a concurrent checkout for the last voucher.
            throw new ApiException(ErrorCode.PROMO_CODE_USAGE_LIMIT);
        }
        redemptionRepository.save(PromoCodeRedemption.builder()
                .promoCodeId(promo.getId())
                .orderId(orderId)
                .customerId(customerId)
                .customerEmail(email)
                .amount(amount)
                .build());
        log.info("Redeemed promo code {} on order {} (-{})", promo.getCode(), orderId, amount);
    }

    /** Returns a voucher when its order is cancelled before payment. */
    @Transactional
    public void release(Long promoCodeId, Long orderId) {
        if (promoCodeId == null) {
            return;
        }
        repository.decrementUsage(promoCodeId);
        redemptionRepository.deleteByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public PromoCode require(Long id) {
        return repository.findAliveById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.PROMO_CODE_INVALID));
    }
}
