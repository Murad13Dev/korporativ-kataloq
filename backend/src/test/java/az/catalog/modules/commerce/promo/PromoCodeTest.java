package az.catalog.modules.commerce.promo;

import az.catalog.common.domain.ContentStatus;
import az.catalog.modules.catalog.discount.DiscountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A promo code reduces the cart total the way a {@code Discount} campaign reduces a
 * product's price — different entity, same kind of money math, so it earns the same kind
 * of pinned-down test rather than being left to integration coverage.
 */
class PromoCodeTest {

    private static final OffsetDateTime NOW = OffsetDateTime.now();

    private static PromoCode percentage(BigDecimal value) {
        return PromoCode.builder()
                .code("SAVE")
                .discountType(DiscountType.PERCENTAGE)
                .value(value)
                .minSubtotal(BigDecimal.ZERO)
                .status(ContentStatus.ACTIVE)
                .build();
    }

    private static PromoCode fixed(BigDecimal value) {
        return PromoCode.builder()
                .code("FLAT10")
                .discountType(DiscountType.FIXED_AMOUNT)
                .value(value)
                .minSubtotal(BigDecimal.ZERO)
                .status(ContentStatus.ACTIVE)
                .build();
    }

    @Nested
    @DisplayName("Endirim məbləğinin hesablanması")
    class DiscountCalculation {

        @Test
        @DisplayName("faiz kodu düzgün hesablanır")
        void percentageDiscount() {
            PromoCode code = percentage(new BigDecimal("10"));
            assertThat(code.discountFor(new BigDecimal("200.00"))).isEqualByComparingTo("20.00");
        }

        @Test
        @DisplayName("faiz nəticəsi HALF_UP qaydası ilə 2 onluğa yuvarlaqlaşdırılır")
        void percentageRoundsHalfUp() {
            PromoCode code = percentage(new BigDecimal("33.333"));
            // 33.333% of 10.00 = 3.3333 -> rounds to 3.33
            assertThat(code.discountFor(new BigDecimal("10.00"))).isEqualByComparingTo("3.33");
        }

        @Test
        @DisplayName("sabit məbləğ kodu olduğu kimi tətbiq olunur")
        void fixedAmountDiscount() {
            PromoCode code = fixed(new BigDecimal("15.00"));
            assertThat(code.discountFor(new BigDecimal("200.00"))).isEqualByComparingTo("15.00");
        }

        @Test
        @DisplayName("maxDiscountAmount faiz endirimini məhdudlaşdırır")
        void percentageIsCapped() {
            PromoCode code = percentage(new BigDecimal("50"));
            code.setMaxDiscountAmount(new BigDecimal("30"));

            // 50% of 200 would be 100, but the code caps it at 30.
            assertThat(code.discountFor(new BigDecimal("200.00"))).isEqualByComparingTo("30.00");
        }

        @Test
        @DisplayName("endirim heç vaxt ara cəmdən böyük ola bilməz")
        void discountNeverExceedsSubtotal() {
            PromoCode code = fixed(new BigDecimal("500.00"));
            assertThat(code.discountFor(new BigDecimal("50.00"))).isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("mənfi və ya sıfır ara cəm üçün endirim sıfırdır")
        void nonPositiveSubtotalYieldsZero() {
            PromoCode code = fixed(new BigDecimal("10.00"));
            assertThat(code.discountFor(BigDecimal.ZERO)).isEqualByComparingTo("0.00");
            assertThat(code.discountFor(null)).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("dəyəri sıfır olan pulsuz-çatdırılma kodu ara cəmi azaltmır")
        void freeDeliveryWithZeroValueDoesNotDiscountSubtotal() {
            PromoCode code = fixed(BigDecimal.ZERO);
            code.setFreeDelivery(true);
            assertThat(code.discountFor(new BigDecimal("100.00"))).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("Aktivlik yoxlaması")
    class RunningCheck {

        @Test
        @DisplayName("tarix məhdudiyyəti olmayan aktiv kod hər zaman işləyir")
        void activeWithoutDatesIsAlwaysRunning() {
            PromoCode code = fixed(new BigDecimal("10"));
            assertThat(code.isRunning(NOW)).isTrue();
        }

        @Test
        @DisplayName("başlama tarixindən əvvəl kod işləmir")
        void beforeStartIsNotRunning() {
            PromoCode code = fixed(new BigDecimal("10"));
            code.setStartsAt(NOW.plusDays(1));
            assertThat(code.isRunning(NOW)).isFalse();
        }

        @Test
        @DisplayName("bitmə tarixindən sonra kod işləmir")
        void afterEndIsNotRunning() {
            PromoCode code = fixed(new BigDecimal("10"));
            code.setEndsAt(NOW.minusDays(1));
            assertThat(code.isRunning(NOW)).isFalse();
        }

        @Test
        @DisplayName("qaralama/arxiv statuslu kod işləmir")
        void inactiveStatusIsNotRunning() {
            PromoCode code = fixed(new BigDecimal("10"));
            code.setStatus(ContentStatus.DRAFT);
            assertThat(code.isRunning(NOW)).isFalse();
        }

        @Test
        @DisplayName("silinmiş kod işləmir")
        void deletedIsNotRunning() {
            PromoCode code = fixed(new BigDecimal("10"));
            code.softDelete();
            assertThat(code.isRunning(NOW)).isFalse();
        }
    }

    @Nested
    @DisplayName("İstifadə tutumu")
    class Capacity {

        @Test
        @DisplayName("limit qoyulmayıbsa tutum həmişə var")
        void noLimitAlwaysHasCapacity() {
            PromoCode code = fixed(new BigDecimal("10"));
            code.setUsedCount(1000);
            assertThat(code.hasCapacity()).isTrue();
        }

        @Test
        @DisplayName("istifadə sayı limitə çatanda tutum bitir")
        void capacityExhaustedAtLimit() {
            PromoCode code = fixed(new BigDecimal("10"));
            code.setUsageLimit(5);
            code.setUsedCount(5);
            assertThat(code.hasCapacity()).isFalse();
        }

        @Test
        @DisplayName("istifadə sayı limitdən azdırsa tutum var")
        void capacityAvailableBelowLimit() {
            PromoCode code = fixed(new BigDecimal("10"));
            code.setUsageLimit(5);
            code.setUsedCount(4);
            assertThat(code.hasCapacity()).isTrue();
        }
    }
}
