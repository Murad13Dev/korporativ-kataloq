package az.catalog.modules.commerce.customer;

import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.modules.catalog.discount.DiscountType;
import az.catalog.modules.commerce.order.Order;
import az.catalog.modules.commerce.order.OrderStatus;
import az.catalog.modules.commerce.order.OrderRepository;
import az.catalog.modules.commerce.promo.PromoCode;
import az.catalog.modules.commerce.promo.PromoCodeRepository;
import az.catalog.modules.notification.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;

/**
 * "Invite a friend" — both sides get a fixed-amount promo code once the invited friend's
 * first order actually completes. Rides on the same {@link PromoCode} minting the loyalty
 * redemption already uses (see {@code LoyaltyService.redeem}) rather than a bespoke
 * discount path, so redeeming a referral reward is identical to typing in any other code.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReferralService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I — hand-typed codes
    private static final int CODE_LENGTH = 7;
    private static final BigDecimal REWARD_AMOUNT = new BigDecimal("10.00");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final MailService mailService;

    /** Every customer gets one, generated once at registration — never reused, never edited. */
    @Transactional
    public String generateReferralCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            String candidate = code.toString();
            if (!customerRepository.existsByReferralCodeIgnoreCase(candidate)) {
                return candidate;
            }
        }
        throw new ApiException(ErrorCode.INTERNAL_ERROR, "Dəvət kodu yaradıla bilmədi");
    }

    /**
     * Resolves a `?ref=` code at registration to the referrer's id. Silently ignores an
     * unknown or self-referral code — a typo'd or malicious code must never block sign-up.
     */
    @Transactional(readOnly = true)
    public Long resolveReferrer(String referralCode, String newCustomerEmail) {
        if (referralCode == null || referralCode.isBlank()) {
            return null;
        }
        return customerRepository.findByReferralCodeIgnoreCase(referralCode.trim())
                .filter(referrer -> !referrer.getEmail().equalsIgnoreCase(newCustomerEmail))
                .map(Customer::getId)
                .orElse(null);
    }

    /**
     * Called from the same order-status switch that awards loyalty points — see
     * {@code OrderService#applyTransition}. Pays out once, on the referred customer's
     * first COMPLETED order; a repeat customer's later orders are a no-op, and so is an
     * order whose customer was never referred or was already rewarded.
     */
    @Transactional
    public void rewardIfFirstOrder(Order order) {
        Customer customer = order.getCustomer();
        if (customer == null || customer.getReferredByCustomerId() == null
                || customer.getReferralRewardedAt() != null) {
            return;
        }
        // Hibernate auto-flushes the just-applied COMPLETED status before this count runs,
        // so "this order" is already counted — more than one means it isn't their first.
        if (orderRepository.countByCustomerIdAndStatus(customer.getId(), OrderStatus.COMPLETED) > 1) {
            return;
        }
        Customer referrer = customerRepository.findAliveById(customer.getReferredByCustomerId()).orElse(null);
        if (referrer == null) {
            return;
        }

        String refereeCode = mintPromoCode("Dəvət mükafatı — dəvət edildi");
        String referrerCode = mintPromoCode("Dəvət mükafatı — dəvət etdi");
        customer.setReferralRewardedAt(OffsetDateTime.now());

        mailService.sendReferralReward(customer.getEmail(), customer.getFirstName(), refereeCode, REWARD_AMOUNT);
        mailService.sendReferralReward(referrer.getEmail(), referrer.getFirstName(), referrerCode, REWARD_AMOUNT);
        log.info("Referral reward paid: referrer={} referee={}", referrer.getId(), customer.getId());
    }

    private String mintPromoCode(String description) {
        String code = generateUniquePromoCode();
        promoCodeRepository.save(PromoCode.builder()
                .code(code)
                .description(description)
                .discountType(DiscountType.FIXED_AMOUNT)
                .value(REWARD_AMOUNT)
                .minSubtotal(BigDecimal.ZERO)
                .usageLimit(1)
                .usageLimitPerCustomer(1)
                .build());
        return code;
    }

    private String generateUniquePromoCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = "INVITE-" + (100000 + RANDOM.nextInt(900000));
            if (!promoCodeRepository.existsByCode(candidate, null)) {
                return candidate;
            }
        }
        throw new ApiException(ErrorCode.INTERNAL_ERROR, "Promo kod yaradıla bilmədi");
    }
}
