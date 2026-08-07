package az.catalog.modules.commerce.customer;

import az.catalog.common.exception.ApiException;
import az.catalog.modules.commerce.order.OrderDtos;
import az.catalog.modules.commerce.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Read-mostly customer administration.
 *
 * There is deliberately no way to edit a customer's own details here: their name, address
 * and password belong to them, and an operator quietly changing them would break the trail
 * on every order already placed. Blocking an account and reading history is the whole
 * surface an operator actually needs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerAdminService {

    private static final int RECENT_ORDERS = 10;

    private final CustomerRepository repository;
    private final CustomerRefreshTokenRepository refreshTokenRepository;
    private final AddressRepository addressRepository;
    private final OrderRepository orderRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public Page<CustomerDtos.CustomerRow> search(String search, Customer.Status status,
                                                 Pageable pageable) {
        Page<Customer> page = repository.search(blankToNull(search), status, pageable);

        // One aggregate query for the whole page rather than a count per row.
        Map<Long, Long> orderCounts = orderCountsFor(page.getContent().stream()
                .map(Customer::getId).toList());

        return page.map(customer -> new CustomerDtos.CustomerRow(
                customer.getId(), customer.getFirstName(), customer.getLastName(),
                customer.getEmail(), customer.getPhone(), customer.getCompanyName(),
                customer.getStatus(), orderCounts.getOrDefault(customer.getId(), 0L),
                customer.getLastLoginAt(), customer.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public CustomerDetail get(Long id) {
        Customer customer = load(id);

        List<OrderDtos.OrderSummary> orders = orderRepository
                .findAllByCustomerIdOrderByCreatedAtDesc(id, PageRequest.of(0, RECENT_ORDERS))
                .map(OrderDtos.OrderSummary::from)
                .getContent();

        var stats = statsFor(id);

        return new CustomerDetail(
                CustomerDtos.CustomerProfile.from(customer),
                customer.getStatus(),
                customer.isGuest(),
                addressRepository.findAllByCustomerIdOrderByIsDefaultDescIdAsc(id).stream()
                        .map(CustomerDtos.AddressResponse::from)
                        .toList(),
                orders,
                stats);
    }

    /**
     * Blocking revokes every live session immediately — leaving a blocked customer signed
     * in until their token expires defeats the point of blocking them.
     */
    @Transactional
    public CustomerDetail changeStatus(Long id, Customer.Status status) {
        Customer customer = load(id);
        customer.setStatus(status);

        if (status == Customer.Status.BLOCKED || status == Customer.Status.INACTIVE) {
            int revoked = refreshTokenRepository.revokeAllForCustomer(id, OffsetDateTime.now());
            log.info("Customer {} set to {} — revoked {} sessions", id, status, revoked);
        }
        return get(id);
    }

    /** Soft delete. Orders keep their snapshot, so history stays readable. */
    @Transactional
    public void delete(Long id) {
        Customer customer = load(id);
        customer.softDelete();
        refreshTokenRepository.revokeAllForCustomer(id, OffsetDateTime.now());
        log.info("Soft deleted customer {}", id);
    }

    // -----------------------------------------------------------------------

    private Map<Long, Long> orderCountsFor(List<Long> customerIds) {
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", customerIds.stream().map(id -> "?").toList());
        return jdbcTemplate.query(
                "SELECT customer_id, COUNT(*) AS total FROM orders WHERE customer_id IN (" + placeholders + ") GROUP BY customer_id",
                rs -> {
                    Map<Long, Long> counts = new java.util.HashMap<>();
                    while (rs.next()) {
                        counts.put(rs.getLong("customer_id"), rs.getLong("total"));
                    }
                    return counts;
                },
                customerIds.toArray());
    }

    private CustomerStats statsFor(Long customerId) {
        return jdbcTemplate.query("""
                SELECT COUNT(*) AS order_count,
                       COALESCE(SUM(CASE WHEN payment_status = 'PAID' THEN total ELSE 0 END), 0) AS lifetime_value,
                       MAX(created_at) AS last_order_at
                FROM orders
                WHERE customer_id = ?
                """,
                rs -> rs.next()
                        ? new CustomerStats(
                        rs.getLong("order_count"),
                        rs.getBigDecimal("lifetime_value"),
                        rs.getObject("last_order_at", OffsetDateTime.class))
                        : new CustomerStats(0, BigDecimal.ZERO, null),
                customerId);
    }

    private Customer load(Long id) {
        return repository.findAliveById(id)
                .orElseThrow(() -> ApiException.notFound("Müştəri", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // --- view models --------------------------------------------------------

    public record CustomerDetail(
            CustomerDtos.CustomerProfile profile,
            Customer.Status status,
            /** True for records created by guest checkout that were never claimed. */
            boolean guest,
            List<CustomerDtos.AddressResponse> addresses,
            List<OrderDtos.OrderSummary> recentOrders,
            CustomerStats stats
    ) {
    }

    public record CustomerStats(
            long orderCount,
            BigDecimal lifetimeValue,
            OffsetDateTime lastOrderAt
    ) {
    }
}
