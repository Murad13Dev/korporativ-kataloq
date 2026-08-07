package az.catalog.modules.commerce.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The order lifecycle is an explicit transition table, not a free-form field — the admin
 * UI, payment callbacks and the expiry job all rely on {@link OrderStatus#canTransitionTo}
 * to agree on what is legal. A wrong entry here would let an order silently skip a step
 * (e.g. jump straight to DELIVERED without ever being CONFIRMED).
 */
class OrderStatusTest {

    @Nested
    @DisplayName("İcazə verilən keçidlər")
    class AllowedTransitions {

        @Test
        @DisplayName("PENDING ödənişə, təsdiqə, ləğvə və vaxtın bitməsinə keçə bilər")
        void pendingTransitions() {
            assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.AWAITING_PAYMENT)).isTrue();
            assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
            assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
            assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.EXPIRED)).isTrue();
        }

        @Test
        @DisplayName("PENDING birbaşa SHIPPED və ya DELIVERED-ə keçə bilməz")
        void pendingCannotSkipAhead() {
            assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
            assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
            assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.COMPLETED)).isFalse();
        }

        @Test
        @DisplayName("CONFIRMED hazırlanmaya, göndərilməyə, ləğvə və geri qaytarılmaya keçə bilər")
        void confirmedTransitions() {
            assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.PROCESSING)).isTrue();
            assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
            assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
            assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
        }

        @Test
        @DisplayName("CONFIRMED-dən sonra geri PENDING-ə qayıtmaq olmaz")
        void confirmedCannotGoBackwards() {
            assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.PENDING)).isFalse();
            assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.AWAITING_PAYMENT)).isFalse();
        }

        @Test
        @DisplayName("DELIVERED yalnız tamamlanmaya və ya geri qaytarılmaya keçə bilər")
        void deliveredTransitions() {
            assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
            assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
            assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        }

        @Test
        @DisplayName("COMPLETED yalnız geri qaytarıla bilər, ləğv edilə bilməz")
        void completedOnlyRefundable() {
            assertThat(OrderStatus.COMPLETED.allowedNext()).containsExactly(OrderStatus.REFUNDED);
        }

        @ParameterizedTest(name = "{0} son statusdur — heç bir keçidə icazə yoxdur")
        @EnumSource(value = OrderStatus.class, names = {"CANCELLED", "REFUNDED", "EXPIRED"})
        @DisplayName("Terminal statuslardan çıxış yoxdur")
        void terminalStatusesHaveNoNext(OrderStatus terminal) {
            assertThat(terminal.allowedNext()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Anbarın geri qaytarılması")
    class StockRelease {

        @ParameterizedTest(name = "{0} anbarı geri qaytarır")
        @EnumSource(value = OrderStatus.class, names = {"CANCELLED", "EXPIRED", "REFUNDED"})
        void releasesStock(OrderStatus status) {
            assertThat(status.releasesStock()).isTrue();
        }

        @ParameterizedTest(name = "{0} anbarı geri qaytarmır")
        @EnumSource(value = OrderStatus.class,
                names = {"CANCELLED", "EXPIRED", "REFUNDED"}, mode = EnumSource.Mode.EXCLUDE)
        void doesNotReleaseStock(OrderStatus status) {
            assertThat(status.releasesStock()).isFalse();
        }
    }

    @Nested
    @DisplayName("Etiketlər")
    class Labels {

        @ParameterizedTest(name = "{0} boş olmayan bir etiketə malikdir")
        @EnumSource(OrderStatus.class)
        @DisplayName("Hər status oxunaqlı etiketə malikdir")
        void everyStatusHasALabel(OrderStatus status) {
            assertThat(status.label()).isNotBlank();
        }
    }
}
