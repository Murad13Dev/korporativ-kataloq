package az.catalog.modules.commerce.loyalty;

import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.modules.catalog.discount.DiscountType;
import az.catalog.modules.commerce.customer.Customer;
import az.catalog.modules.commerce.customer.CustomerRepository;
import az.catalog.modules.commerce.order.Order;
import az.catalog.modules.commerce.promo.PromoCode;
import az.catalog.modules.commerce.promo.PromoCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.OffsetDateTime;

/**
 * Spend-to-earn points, redeemable for a one-time promo code.
 *
 * Deliberately rides on the existing promo-code engine for redemption rather than
 * teaching the cart/checkout pricing pipeline a second discount mechanism: a redemption
 * mints an ordinary {@link PromoCode} (single use, fixed amount) that the customer
 * applies exactly like any other voucher.
 */
@Service
@RequiredArgsConstructor
public class LoyaltyService {

    /** 1 AZN spent (rounded down) earns 1 point. */
    private static final int POINTS_PER_CURRENCY_UNIT = 1;

    /** Redemptions are in blocks of 100 points; each block is worth 5 units of the store currency. */
    private static final int POINTS_PER_BLOCK = 100;
    private static final BigDecimal VALUE_PER_BLOCK = new BigDecimal("5.00");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final LoyaltyTransactionRepository repository;
    private final CustomerRepository customerRepository;
    private final PromoCodeRepository promoCodeRepository;

    @Transactional(readOnly = true)
    public LoyaltyDtos.BalanceDto balance(Long customerId) {
        return new LoyaltyDtos.BalanceDto(
                repository.balanceOf(customerId), POINTS_PER_BLOCK, POINTS_PER_BLOCK, VALUE_PER_BLOCK);
    }

    @Transactional(readOnly = true)
    public Page<LoyaltyDtos.TransactionDto> history(Long customerId, Pageable pageable) {
        return repository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(LoyaltyDtos.TransactionDto::from);
    }

    /** Awards points once a completed order's total is final; a no-op if already awarded. */
    @Transactional
    public void earnForOrder(Order order) {
        if (order.getCustomer() == null || repository.existsByOrderIdAndType(order.getId(), LoyaltyTransaction.Type.EARNED)) {
            return;
        }
        int points = order.getTotal().setScale(0, RoundingMode.DOWN).intValue() * POINTS_PER_CURRENCY_UNIT;
        if (points <= 0) {
            return;
        }
        repository.save(LoyaltyTransaction.builder()
                .customer(order.getCustomer())
                .points(points)
                .type(LoyaltyTransaction.Type.EARNED)
                .orderId(order.getId())
                .description("Sifariş #" + order.getOrderNumber())
                .createdAt(OffsetDateTime.now())
                .build());
    }

    @Transactional
    public LoyaltyDtos.RedeemResponse redeem(Long customerId, int points) {
        if (points % POINTS_PER_BLOCK != 0) {
            throw ApiException.field(ErrorCode.VALIDATION_ERROR, "points",
                    "%d-in mislinə bərabər xal seçin".formatted(POINTS_PER_BLOCK));
        }
        int balance = repository.balanceOf(customerId);
        if (points > balance) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "Kifayət qədər xalınız yoxdur");
        }

        Customer customer = customerRepository.findAliveById(customerId)
                .orElseThrow(() -> ApiException.notFound("Müştəri", customerId));

        int blocks = points / POINTS_PER_BLOCK;
        BigDecimal amount = VALUE_PER_BLOCK.multiply(BigDecimal.valueOf(blocks));
        String code = generateCode();

        promoCodeRepository.save(PromoCode.builder()
                .code(code)
                .description("Loyallıq xallarının dəyişdirilməsi")
                .discountType(DiscountType.FIXED_AMOUNT)
                .value(amount)
                .minSubtotal(BigDecimal.ZERO)
                .usageLimit(1)
                .usageLimitPerCustomer(1)
                .build());

        repository.save(LoyaltyTransaction.builder()
                .customer(customer)
                .points(-points)
                .type(LoyaltyTransaction.Type.REDEEMED)
                .description("Promo kod: " + code)
                .createdAt(OffsetDateTime.now())
                .build());

        return new LoyaltyDtos.RedeemResponse(code, amount, balance - points);
    }

    private String generateCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = "LOYAL-" + (100000 + RANDOM.nextInt(900000));
            if (!promoCodeRepository.existsByCode(candidate, null)) {
                return candidate;
            }
        }
        throw new ApiException(ErrorCode.INTERNAL_ERROR, "Promo kod yaradıla bilmədi");
    }
}
