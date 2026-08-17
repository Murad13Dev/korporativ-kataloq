package az.catalog.modules.dashboard;

import az.catalog.common.domain.ContentStatus;
import az.catalog.modules.commerce.order.OrderStatus;
import az.catalog.modules.commerce.order.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    /**
     * The admin landing screen.
     *
     * Deliberately built only from data the system actually owns. The original plan noted
     * that traffic and revenue charts must not be faked before an analytics system exists —
     * an invented graph is worse than no graph, because people make decisions on it.
     */
    public record Summary(
            ProductStats products,
            CategoryStats categories,
            OrderStats orders,
            CustomerStats customers,
            ContentStats content,
            DiscountStats discounts,
            MediaStats media,
            List<RecentOrder> recentOrders,
            List<RecentContactRequest> recentContactRequests,
            List<LowStockProduct> lowStockProducts,
            List<AlertItem> alerts
    ) {
    }

    public record ProductStats(long total, long active, long draft, long archived, long lowStock) {
    }

    public record CategoryStats(long total) {
    }

    /**
     * @param revenueTotal    lifetime, paid orders only
     * @param revenueThisMonth so the operator sees momentum rather than a single number
     */
    public record OrderStats(
            long total,
            long pending,
            long awaitingPayment,
            long processing,
            long completed,
            long cancelled,
            long todayCount,
            long monthCount,
            BigDecimal revenueTotal,
            BigDecimal revenueThisMonth,
            String currency
    ) {
    }

    public record CustomerStats(long total, long newThisMonth) {
    }

    public record ContentStats(long posts, long publishedPosts, long contactRequests, long newContactRequests) {
    }

    public record DiscountStats(long running, long scheduled, long discountedProducts) {
    }

    public record MediaStats(long fileCount, long totalBytes) {
    }

    public record RecentOrder(
            Long id,
            String orderNumber,
            String customerName,
            OrderStatus status,
            String statusLabel,
            PaymentStatus paymentStatus,
            BigDecimal total,
            String currency,
            OffsetDateTime createdAt
    ) {
    }

    public record RecentContactRequest(
            Long id,
            String name,
            String email,
            String subject,
            String status,
            OffsetDateTime createdAt
    ) {
    }

    public record LowStockProduct(
            Long id,
            String name,
            String sku,
            int stockQuantity,
            int threshold
    ) {
    }

    /**
     * Things that need a human decision, surfaced above the numbers.
     *
     * @param severity INFO | WARNING | DANGER — drives the colour, not the wording
     * @param actionUrl admin-relative link that resolves the alert
     */
    public record AlertItem(
            String code,
            String severity,
            String message,
            String actionUrl,
            long count
    ) {
    }

    public record ProductStatusCount(ContentStatus status, long count) {
    }

    /** Live, in-memory — see {@code ProductPresenceService}. Never cached, never stored. */
    public record LiveVisitorsSnapshot(int totalViewers, List<LiveProduct> topProducts) {
    }

    public record LiveProduct(Long id, String name, String slug, int viewers) {
    }

    /**
     * "Anbar radar" — a product's sell-through rate projected against its current stock.
     * @param dailyVelocity units/day averaged over the last 30 days of real orders
     * @param estimatedDaysRemaining {@code stockQuantity / dailyVelocity}, floored
     */
    public record StockForecastItem(
            Long id, String name, String sku, int stockQuantity, double dailyVelocity, int estimatedDaysRemaining) {
    }

    /** How much of the catalog/content actually exists in one non-default language. */
    public record LanguageCoverage(
            String code, String name,
            ContentCoverage products, ContentCoverage categories, ContentCoverage posts, ContentCoverage pages) {
    }

    public record ContentCoverage(long total, long translated) {
        public long missing() {
            return total - translated;
        }
    }
}
