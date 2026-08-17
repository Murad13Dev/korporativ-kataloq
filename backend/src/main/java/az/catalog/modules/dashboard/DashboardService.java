package az.catalog.modules.dashboard;

import az.catalog.common.domain.ContentStatus;
import az.catalog.modules.catalog.category.CategoryRepository;
import az.catalog.modules.catalog.discount.DiscountRepository;
import az.catalog.modules.catalog.product.Product;
import az.catalog.modules.catalog.product.ProductPresenceService;
import az.catalog.modules.catalog.product.ProductRepository;
import az.catalog.modules.commerce.customer.CustomerRepository;
import az.catalog.modules.commerce.order.Order;
import az.catalog.modules.commerce.order.OrderRepository;
import az.catalog.modules.commerce.order.OrderStatus;
import az.catalog.modules.language.LanguageService;
import az.catalog.modules.media.MediaFileRepository;
import az.catalog.modules.siteconfig.SiteConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Aggregates the admin landing screen.
 *
 * Everything here is a real count from a real table. The original plan was explicit that
 * traffic and revenue charts must not be invented before an analytics system exists, so
 * this deliberately reports only what the platform genuinely knows.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int RECENT_LIMIT = 8;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final DiscountRepository discountRepository;
    private final MediaFileRepository mediaFileRepository;
    private final LanguageService languageService;
    private final SiteConfigurationService siteConfigurationService;
    private final ProductPresenceService presenceService;

    /**
     * Blog posts and contact requests have tables but no service module yet, so their
     * counts are read directly. Replace these with repository calls when those modules land.
     */
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public DashboardDtos.Summary summary(String language) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startOfToday = now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime startOfMonth = now.withDayOfMonth(1).toLocalDate()
                .atStartOfDay().atOffset(ZoneOffset.UTC);

        String currency = siteConfigurationService.publishedSnapshot().currency();
        String defaultLanguage = languageService.defaultLanguageCode();

        var products = productStats();
        var orders = orderStats(startOfToday, startOfMonth, currency);
        var discounts = discountStats(now);
        var content = contentStats();

        return new DashboardDtos.Summary(
                products,
                new DashboardDtos.CategoryStats(categoryRepository.countByDeletedAtIsNull()),
                orders,
                new DashboardDtos.CustomerStats(
                        customerRepository.countByDeletedAtIsNull(),
                        customerRepository.countCreatedSince(startOfMonth)),
                content,
                discounts,
                new DashboardDtos.MediaStats(
                        mediaFileRepository.count(),
                        mediaFileRepository.totalStorageBytes()),
                recentOrders(),
                recentContactRequests(),
                lowStockProducts(language, defaultLanguage),
                buildAlerts(products, orders, content, discounts));
    }

    /**
     * The one truly "live" tile on the dashboard — everything else here is a query result
     * as of the moment the page loaded, but this reads the same in-memory presence map the
     * storefront's own "N people viewing" badge does, so an admin watching this screen
     * sees traffic land in something close to real time.
     */
    @Transactional(readOnly = true)
    public DashboardDtos.LiveVisitorsSnapshot liveVisitors(String language) {
        Map<Long, Integer> byProduct = presenceService.snapshot();
        if (byProduct.isEmpty()) {
            return new DashboardDtos.LiveVisitorsSnapshot(0, List.of());
        }

        String fallback = languageService.defaultLanguageCode();
        Map<Long, Product> products = productRepository.findAllById(byProduct.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        int total = byProduct.values().stream().mapToInt(Integer::intValue).sum();
        List<DashboardDtos.LiveProduct> top = byProduct.entrySet().stream()
                .limit(RECENT_LIMIT)
                .map(entry -> {
                    Product product = products.get(entry.getKey());
                    if (product == null) {
                        return null;
                    }
                    var translation = product.translation(language, fallback);
                    String name = translation != null ? translation.getName() : product.getSku();
                    return new DashboardDtos.LiveProduct(product.getId(), name, product.getSlug(), entry.getValue());
                })
                .filter(Objects::nonNull)
                .toList();

        return new DashboardDtos.LiveVisitorsSnapshot(total, top);
    }

    private static final int VELOCITY_WINDOW_DAYS = 30;
    private static final int FORECAST_ALERT_DAYS = 14;

    /**
     * "Anbar radar" — unlike {@link #lowStockProducts}, which only flags what's already
     * thin, this projects forward from real sales velocity so an operator can reorder
     * before the count actually hits zero. A product with no sales in the window has no
     * defined runway and is excluded rather than reported as "infinite days left".
     */
    @Transactional(readOnly = true)
    public List<DashboardDtos.StockForecastItem> stockForecast(String language) {
        OffsetDateTime since = OffsetDateTime.now().minusDays(VELOCITY_WINDOW_DAYS);
        List<ProductRepository.StockVelocityRow> rows = productRepository.findStockVelocity(since);
        if (rows.isEmpty()) {
            return List.of();
        }

        String fallback = languageService.defaultLanguageCode();
        Map<Long, Product> products = productRepository.findAllById(
                        rows.stream().map(ProductRepository.StockVelocityRow::getId).toList()).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        record Projected(ProductRepository.StockVelocityRow row, double dailyVelocity, int daysRemaining) {
        }

        return rows.stream()
                .map(row -> {
                    double dailyVelocity = row.getSold() / (double) VELOCITY_WINDOW_DAYS;
                    int daysRemaining = (int) Math.floor(row.getStockQuantity() / dailyVelocity);
                    return new Projected(row, dailyVelocity, daysRemaining);
                })
                .filter(projected -> projected.daysRemaining() <= FORECAST_ALERT_DAYS)
                .sorted(Comparator.comparingInt(Projected::daysRemaining))
                .limit(RECENT_LIMIT)
                .map(projected -> {
                    Product product = products.get(projected.row().getId());
                    if (product == null) {
                        return null;
                    }
                    var translation = product.translation(language, fallback);
                    String name = translation != null ? translation.getName() : product.getSku();
                    return new DashboardDtos.StockForecastItem(
                            product.getId(), name, product.getSku(), projected.row().getStockQuantity(),
                            Math.round(projected.dailyVelocity() * 100) / 100.0, projected.daysRemaining());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // -----------------------------------------------------------------------

    private DashboardDtos.ProductStats productStats() {
        return new DashboardDtos.ProductStats(
                productRepository.countByDeletedAtIsNull(),
                productRepository.countByStatus(ContentStatus.ACTIVE),
                productRepository.countByStatus(ContentStatus.DRAFT),
                productRepository.countByStatus(ContentStatus.ARCHIVED),
                productRepository.countLowStock());
    }

    private DashboardDtos.OrderStats orderStats(OffsetDateTime startOfToday,
                                                OffsetDateTime startOfMonth,
                                                String currency) {
        return new DashboardDtos.OrderStats(
                orderRepository.count(),
                orderRepository.countByStatus(OrderStatus.PENDING),
                orderRepository.countByStatus(OrderStatus.AWAITING_PAYMENT),
                orderRepository.countByStatus(OrderStatus.PROCESSING),
                orderRepository.countByStatus(OrderStatus.COMPLETED),
                orderRepository.countByStatus(OrderStatus.CANCELLED),
                orderRepository.countCreatedSince(startOfToday),
                orderRepository.countCreatedSince(startOfMonth),
                orderRepository.totalRevenue(),
                orderRepository.revenueSince(startOfMonth),
                currency);
    }

    private DashboardDtos.DiscountStats discountStats(OffsetDateTime now) {
        long scheduled = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM discounts
                WHERE deleted_at IS NULL AND status = 'ACTIVE'
                  AND starts_at IS NOT NULL AND starts_at > now()
                """, Long.class);

        return new DashboardDtos.DiscountStats(
                discountRepository.countRunning(now),
                scheduled,
                discountRepository.findDiscountedProductIds(now).size());
    }

    private DashboardDtos.ContentStats contentStats() {
        long posts = countOrZero("SELECT COUNT(*) FROM posts WHERE deleted_at IS NULL");
        long published = countOrZero(
                "SELECT COUNT(*) FROM posts WHERE deleted_at IS NULL AND status = 'ACTIVE'");
        long contactRequests = countOrZero("SELECT COUNT(*) FROM contact_requests");
        long newContactRequests = countOrZero(
                "SELECT COUNT(*) FROM contact_requests WHERE status = 'NEW'");

        return new DashboardDtos.ContentStats(posts, published, contactRequests, newContactRequests);
    }

    private List<DashboardDtos.RecentOrder> recentOrders() {
        return orderRepository.findRecent(PageRequest.of(0, RECENT_LIMIT)).stream()
                .map(this::toRecentOrder)
                .toList();
    }

    private DashboardDtos.RecentOrder toRecentOrder(Order order) {
        return new DashboardDtos.RecentOrder(
                order.getId(), order.getOrderNumber(), order.getCustomerName(),
                order.getStatus(), order.getStatus().label(), order.getPaymentStatus(),
                order.getTotal(), order.getCurrency(), order.getCreatedAt());
    }

    private List<DashboardDtos.RecentContactRequest> recentContactRequests() {
        try {
            return jdbcTemplate.query("""
                    SELECT id, name, email, subject, status, created_at
                    FROM contact_requests
                    ORDER BY created_at DESC
                    LIMIT ?
                    """,
                    (rs, rowNum) -> new DashboardDtos.RecentContactRequest(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("subject"),
                            rs.getString("status"),
                            rs.getObject("created_at", OffsetDateTime.class)),
                    RECENT_LIMIT);
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<DashboardDtos.LowStockProduct> lowStockProducts(String language, String defaultLanguage) {
        return productRepository.findLowStock(PageRequest.of(0, RECENT_LIMIT)).stream()
                .map(product -> toLowStock(product, language, defaultLanguage))
                .toList();
    }

    private DashboardDtos.LowStockProduct toLowStock(Product product, String language, String fallback) {
        var translation = product.translation(language, fallback);
        return new DashboardDtos.LowStockProduct(
                product.getId(),
                translation != null ? translation.getName() : product.getSku(),
                product.getSku(),
                product.getStockQuantity(),
                product.getLowStockThreshold());
    }

    /**
     * Turns the numbers into things worth acting on. Only non-zero conditions are
     * emitted — a dashboard full of green "0 problems" rows trains people to ignore it.
     */
    private List<DashboardDtos.AlertItem> buildAlerts(DashboardDtos.ProductStats products,
                                                      DashboardDtos.OrderStats orders,
                                                      DashboardDtos.ContentStats content,
                                                      DashboardDtos.DiscountStats discounts) {
        List<DashboardDtos.AlertItem> alerts = new ArrayList<>();

        if (orders.awaitingPayment() > 0) {
            alerts.add(new DashboardDtos.AlertItem(
                    "ORDERS_AWAITING_PAYMENT", "WARNING",
                    "%d sifariş ödəniş gözləyir".formatted(orders.awaitingPayment()),
                    "/admin/orders?status=AWAITING_PAYMENT", orders.awaitingPayment()));
        }
        if (orders.pending() > 0) {
            alerts.add(new DashboardDtos.AlertItem(
                    "ORDERS_PENDING", "INFO",
                    "%d yeni sifariş emal gözləyir".formatted(orders.pending()),
                    "/admin/orders?status=PENDING", orders.pending()));
        }
        if (products.lowStock() > 0) {
            alerts.add(new DashboardDtos.AlertItem(
                    "LOW_STOCK", "WARNING",
                    "%d məhsulun anbar qalığı azdır".formatted(products.lowStock()),
                    "/admin/products?lowStock=true", products.lowStock()));
        }
        if (content.newContactRequests() > 0) {
            alerts.add(new DashboardDtos.AlertItem(
                    "NEW_CONTACT_REQUESTS", "INFO",
                    "%d cavablanmamış əlaqə sorğusu".formatted(content.newContactRequests()),
                    "/admin/contact-requests?status=NEW", content.newContactRequests()));
        }
        if (products.draft() > 0) {
            alerts.add(new DashboardDtos.AlertItem(
                    "DRAFT_PRODUCTS", "INFO",
                    "%d məhsul qaralama vəziyyətindədir".formatted(products.draft()),
                    "/admin/products?status=DRAFT", products.draft()));
        }
        if (discounts.running() > 0) {
            alerts.add(new DashboardDtos.AlertItem(
                    "RUNNING_DISCOUNTS", "INFO",
                    "%d kampaniya hazırda aktivdir".formatted(discounts.running()),
                    "/admin/discounts", discounts.running()));
        }

        return alerts;
    }

    /** Tolerates a table that a not-yet-written module owns. */
    private long countOrZero(String sql) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class);
            return value == null ? 0 : value;
        } catch (RuntimeException ex) {
            return 0;
        }
    }
}
