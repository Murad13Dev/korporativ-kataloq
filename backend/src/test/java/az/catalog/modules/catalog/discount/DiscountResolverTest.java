package az.catalog.modules.catalog.discount;

import az.catalog.common.domain.ContentStatus;
import az.catalog.modules.catalog.category.Category;
import az.catalog.modules.catalog.product.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The discount engine is the one place where a bug shows up directly on a customer's
 * invoice, so its rules are pinned down here rather than left to integration coverage.
 */
class DiscountResolverTest {

    private static final OffsetDateTime NOW = OffsetDateTime.now();

    // -----------------------------------------------------------------------
    // Saving calculation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Endirim məbləğinin hesablanması")
    class SavingCalculation {

        @Test
        @DisplayName("faiz endirimi düzgün hesablanır")
        void percentageDiscount() {
            Discount discount = percentage("20%", new BigDecimal("20"), 0);

            assertThat(discount.savingFor(new BigDecimal("250.00")))
                    .isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("sabit məbləğ endirimi olduğu kimi tətbiq olunur")
        void fixedAmountDiscount() {
            Discount discount = fixed("30 ₼", new BigDecimal("30"), 0);

            assertThat(discount.savingFor(new BigDecimal("250.00")))
                    .isEqualByComparingTo("30.00");
        }

        @Test
        @DisplayName("maxDiscountAmount faiz endirimini məhdudlaşdırır")
        void percentageIsCapped() {
            Discount discount = percentage("30%, maks 50", new BigDecimal("30"), 0);
            discount.setMaxDiscountAmount(new BigDecimal("50"));

            // 30% of 1000 would be 300, but the campaign caps it at 50.
            assertThat(discount.savingFor(new BigDecimal("1000.00")))
                    .isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("endirim heç vaxt qiymətdən böyük ola bilməz")
        void savingNeverExceedsPrice() {
            // A mis-typed fixed campaign larger than the product price must not produce
            // a negative final price.
            Discount discount = fixed("100 ₼", new BigDecimal("100"), 0);

            assertThat(discount.savingFor(new BigDecimal("79.00")))
                    .isEqualByComparingTo("79.00");
        }

        @Test
        @DisplayName("qiyməti olmayan məhsul üçün endirim sıfırdır")
        void noPriceMeansNoSaving() {
            Discount discount = percentage("20%", new BigDecimal("20"), 0);

            assertThat(discount.savingFor(null)).isEqualByComparingTo("0");
            assertThat(discount.savingFor(BigDecimal.ZERO)).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("sabit endirim üçün də faiz göstəricisi hesablanır")
        void percentageIsDerivedForFixedCampaigns() {
            Discount discount = fixed("30 ₼", new BigDecimal("30"), 0);

            // The ribbon always shows a percentage, whatever the campaign type is.
            assertThat(discount.percentageFor(new BigDecimal("120.00"))).isEqualTo(25);
        }
    }

    // -----------------------------------------------------------------------
    // Campaign windows
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Kampaniya vaxt aralığı")
    class Scheduling {

        @Test
        @DisplayName("aktiv və aralıqda olan kampaniya işləyir")
        void runningCampaign() {
            Discount discount = percentage("Yaz", new BigDecimal("20"), 0);
            discount.setStartsAt(NOW.minusDays(1));
            discount.setEndsAt(NOW.plusDays(1));

            assertThat(discount.isRunning(NOW)).isTrue();
        }

        @Test
        @DisplayName("başlamamış kampaniya işləmir")
        void notYetStarted() {
            Discount discount = percentage("Gələcək", new BigDecimal("20"), 0);
            discount.setStartsAt(NOW.plusDays(1));

            assertThat(discount.isRunning(NOW)).isFalse();
            assertThat(discount.isScheduled(NOW)).isTrue();
        }

        @Test
        @DisplayName("vaxtı bitmiş kampaniya işləmir")
        void expired() {
            Discount discount = percentage("Keçmiş", new BigDecimal("20"), 0);
            discount.setEndsAt(NOW.minusHours(1));

            assertThat(discount.isRunning(NOW)).isFalse();
            assertThat(discount.hasExpired(NOW)).isTrue();
        }

        @Test
        @DisplayName("DRAFT statuslu kampaniya tarixdən asılı olmayaraq işləmir")
        void draftNeverRuns() {
            Discount discount = percentage("Qaralama", new BigDecimal("20"), 0);
            discount.setStatus(ContentStatus.DRAFT);

            assertThat(discount.isRunning(NOW)).isFalse();
        }

        @Test
        @DisplayName("tarixi olmayan kampaniya həmişə işləyir")
        void openEndedCampaign() {
            Discount discount = percentage("Daimi", new BigDecimal("10"), 0);

            assertThat(discount.isRunning(NOW)).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // Resolution rules — the part that decides which campaign wins
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Kampaniya seçimi qaydaları")
    class Resolution {

        @Test
        @DisplayName("yüksək prioritetli kampaniya qalib gəlir")
        void higherPriorityWins() {
            Discount low = allProducts("10%", new BigDecimal("10"), 1);
            Discount high = allProducts("25%", new BigDecimal("25"), 50);

            var context = context(low, high);
            Product product = product("249.00", null);

            assertThat(context.bestFor(product)).contains(high);
            assertThat(context.priceFor(product).finalPrice()).isEqualByComparingTo("186.75");
        }

        @Test
        @DisplayName("prioritet bərabərdirsə daha spesifik scope qalib gəlir")
        void moreSpecificScopeWinsOnTie() {
            Category category = category(10L, "10");

            Discount storeWide = allProducts("Hamısı 20%", new BigDecimal("20"), 5);
            Discount categoryWide = categories("Kateqoriya 20%", new BigDecimal("20"), 5, Set.of(10L));
            Discount productOnly = products("Məhsul 20%", new BigDecimal("20"), 5, Set.of(1L));

            var context = context(storeWide, categoryWide, productOnly);
            Product product = product("100.00", category);

            assertThat(context.bestFor(product)).contains(productOnly);
        }

        @Test
        @DisplayName("prioritet və scope bərabərdirsə müştəri üçün ən sərfəli qalib gəlir")
        void largestSavingWinsOnFullTie() {
            Discount smaller = allProducts("10%", new BigDecimal("10"), 0);
            Discount larger = allProducts("35%", new BigDecimal("35"), 0);

            var context = context(smaller, larger);

            assertThat(context.bestFor(product("200.00", null))).contains(larger);
        }

        @Test
        @DisplayName("kateqoriya kampaniyası alt kateqoriyalara da tətbiq olunur")
        void categoryCampaignCoversDescendants() {
            // "Mebel" (1) → "Oturacaqlar" (5): a campaign on the parent must reach the child.
            Category child = category(5L, "1/5");
            Discount campaign = categories("Mebel 20%", new BigDecimal("20"), 10, Set.of(1L));

            var context = context(campaign);

            assertThat(context.bestFor(product("500.00", child))).contains(campaign);
        }

        @Test
        @DisplayName("uyğun olmayan kateqoriyaya kampaniya tətbiq olunmur")
        void unrelatedCategoryIsNotMatched() {
            Category electronics = category(3L, "3");
            Discount furnitureOnly = categories("Mebel", new BigDecimal("20"), 10, Set.of(1L));

            var context = context(furnitureOnly);

            assertThat(context.bestFor(product("500.00", electronics))).isEmpty();
        }

        @Test
        @DisplayName("uyğun kampaniya yoxdursa qiymət dəyişmir")
        void noCampaignLeavesPriceUntouched() {
            var context = context();
            var price = context.priceFor(product("249.00", null));

            assertThat(price.discounted()).isFalse();
            assertThat(price.finalPrice()).isEqualByComparingTo("249.00");
            assertThat(price.savingAmount()).isEqualByComparingTo("0");
            assertThat(price.badgeLabel()).isNull();
        }

        @Test
        @DisplayName("qiyməti gizli məhsul heç vaxt endirimli görünmür")
        void hiddenPriceIsNeverDiscounted() {
            Discount campaign = allProducts("50%", new BigDecimal("50"), 10);
            Product product = product("1000.00", null);
            product.setShowPrice(false);

            var price = context(campaign).priceFor(product);

            // There is nothing meaningful to strike through when the price is hidden.
            assertThat(price.discounted()).isFalse();
        }

        @Test
        @DisplayName("kampaniyalar üst-üstə gəlmir — yalnız biri tətbiq olunur")
        void campaignsDoNotStack() {
            Discount first = allProducts("20%", new BigDecimal("20"), 10);
            Discount second = allProducts("30%", new BigDecimal("30"), 20);

            var price = context(first, second).priceFor(product("100.00", null));

            // 30% only — never 20% then a further 30% off the result (which would be 56).
            assertThat(price.finalPrice()).isEqualByComparingTo("70.00");
        }
    }

    // -----------------------------------------------------------------------
    // Ribbon presentation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Lent (badge) mətni")
    class BadgeLabels {

        @Test
        @DisplayName("standart lent faizi göstərir")
        void defaultLabelShowsPercentage() {
            Discount discount = percentage("Yaz", new BigDecimal("25"), 0);

            var price = PriceInfo.of(discount, new BigDecimal("200.00"), "AZN");

            assertThat(price.badgeLabel()).isEqualTo("-25%");
            assertThat(price.savingPercentage()).isEqualTo(25);
        }

        @Test
        @DisplayName("badgeLabel təyin edilibsə avtomatik mətn əvəzlənir")
        void explicitLabelOverridesPercentage() {
            Discount discount = percentage("Qara cümə", new BigDecimal("40"), 0);
            discount.setBadgeLabel("BLACK FRIDAY");

            assertThat(PriceInfo.of(discount, new BigDecimal("200.00"), "AZN").badgeLabel())
                    .isEqualTo("BLACK FRIDAY");
        }

        @Test
        @DisplayName("çox kiçik endirimdə faiz əvəzinə məbləğ göstərilir")
        void tinySavingFallsBackToAmount() {
            // 0.4% would round to "-0%", which reads as a bug on the card.
            Discount discount = fixed("1 ₼", new BigDecimal("1"), 0);

            var price = PriceInfo.of(discount, new BigDecimal("500.00"), "AZN");

            assertThat(price.badgeLabel()).isEqualTo("-1 AZN");
        }

        @Test
        @DisplayName("miqdara görə ümumi qənaət hesablanır")
        void savingScalesWithQuantity() {
            Discount discount = percentage("20%", new BigDecimal("20"), 0);
            var price = PriceInfo.of(discount, new BigDecimal("100.00"), "AZN");

            assertThat(price.totalFor(3)).isEqualByComparingTo("240.00");
            assertThat(price.savingFor(3)).isEqualByComparingTo("60.00");
        }
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private static DiscountResolver.DiscountContext context(Discount... discounts) {
        return new DiscountResolver.DiscountContext(List.of(discounts), NOW);
    }

    private static Product product(String price, Category category) {
        Product product = new Product();
        product.setId(1L);
        product.setSku("TEST-1");
        product.setPrice(new BigDecimal(price));
        product.setCurrency("AZN");
        product.setShowPrice(true);
        product.setStatus(ContentStatus.ACTIVE);
        product.setCategory(category);
        return product;
    }

    private static Category category(Long id, String path) {
        Category category = new Category();
        category.setId(id);
        category.setPath(path);
        return category;
    }

    private static Discount base(String name, DiscountType type, BigDecimal value, int priority) {
        Discount discount = new Discount();
        discount.setId((long) name.hashCode());
        discount.setName(name);
        discount.setDiscountType(type);
        discount.setValue(value);
        discount.setPriority(priority);
        discount.setStatus(ContentStatus.ACTIVE);
        discount.setShowBadge(true);
        discount.setBadgeStyle(BadgeStyle.SALE);
        return discount;
    }

    private static Discount percentage(String name, BigDecimal value, int priority) {
        Discount discount = base(name, DiscountType.PERCENTAGE, value, priority);
        discount.setScope(DiscountScope.ALL_PRODUCTS);
        return discount;
    }

    private static Discount fixed(String name, BigDecimal value, int priority) {
        Discount discount = base(name, DiscountType.FIXED_AMOUNT, value, priority);
        discount.setScope(DiscountScope.ALL_PRODUCTS);
        return discount;
    }

    private static Discount allProducts(String name, BigDecimal value, int priority) {
        return percentage(name, value, priority);
    }

    private static Discount categories(String name, BigDecimal value, int priority, Set<Long> categoryIds) {
        Discount discount = base(name, DiscountType.PERCENTAGE, value, priority);
        discount.setScope(DiscountScope.CATEGORIES);
        discount.setCategoryIds(new java.util.LinkedHashSet<>(categoryIds));
        return discount;
    }

    private static Discount products(String name, BigDecimal value, int priority, Set<Long> productIds) {
        Discount discount = base(name, DiscountType.PERCENTAGE, value, priority);
        discount.setScope(DiscountScope.PRODUCTS);
        discount.setProductIds(new java.util.LinkedHashSet<>(productIds));
        return discount;
    }
}
