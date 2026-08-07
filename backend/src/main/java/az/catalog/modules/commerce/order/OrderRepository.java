package az.catalog.modules.commerce.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"items", "customer"})
    Optional<Order> findByOrderNumber(String orderNumber);

    @EntityGraph(attributePaths = {"items", "customer"})
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findDetailById(@Param("id") Long id);

    /** Guest order lookup: the number alone is not enough, the e-mail must match too. */
    @EntityGraph(attributePaths = {"items"})
    @Query("""
            SELECT o FROM Order o
            WHERE o.orderNumber = :orderNumber AND lower(o.customerEmail) = lower(:email)
            """)
    Optional<Order> findForGuest(@Param("orderNumber") String orderNumber, @Param("email") String email);

    @EntityGraph(attributePaths = {"items"})
    Page<Order> findAllByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Optional<Order> findByIdAndCustomerId(Long id, Long customerId);

    @Query("""
            SELECT o FROM Order o
            WHERE (:search IS NULL OR lower(o.orderNumber) LIKE lower(concat('%', :search, '%'))
                                   OR lower(o.customerName) LIKE lower(concat('%', :search, '%'))
                                   OR lower(o.customerEmail) LIKE lower(concat('%', :search, '%'))
                                   OR o.customerPhone LIKE concat('%', :search, '%'))
              AND (:status IS NULL OR o.status = :status)
              AND (:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus)
              AND (:from IS NULL OR o.createdAt >= :from)
              AND (:to IS NULL OR o.createdAt <= :to)
            """)
    Page<Order> search(@Param("search") String search,
                       @Param("status") OrderStatus status,
                       @Param("paymentStatus") PaymentStatus paymentStatus,
                       @Param("from") OffsetDateTime from,
                       @Param("to") OffsetDateTime to,
                       Pageable pageable);

    /** Orders whose payment window has elapsed — picked up by the expiry job. */
    @Query("""
            SELECT o FROM Order o
            WHERE o.status = az.catalog.modules.commerce.order.OrderStatus.AWAITING_PAYMENT
              AND o.paymentExpiresAt IS NOT NULL AND o.paymentExpiresAt < :now
            """)
    List<Order> findExpiredAwaitingPayment(@Param("now") OffsetDateTime now);

    /** Highest sequence used today, for the ORD-YYYYMMDD-NNNN counter. */
    @Query(value = "SELECT nextval('order_number_seq')", nativeQuery = true)
    Long nextOrderSequence();

    // --- dashboard ---------------------------------------------------------

    long count();

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status")
    long countByStatus(@Param("status") OrderStatus status);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :since")
    long countCreatedSince(@Param("since") OffsetDateTime since);

    @Query("""
            SELECT COALESCE(SUM(o.total), 0) FROM Order o
            WHERE o.paymentStatus = az.catalog.modules.commerce.order.PaymentStatus.PAID
              AND o.createdAt >= :since
            """)
    BigDecimal revenueSince(@Param("since") OffsetDateTime since);

    @Query("""
            SELECT COALESCE(SUM(o.total), 0) FROM Order o
            WHERE o.paymentStatus = az.catalog.modules.commerce.order.PaymentStatus.PAID
            """)
    BigDecimal totalRevenue();

    @EntityGraph(attributePaths = {"items"})
    @Query("SELECT o FROM Order o ORDER BY o.createdAt DESC")
    List<Order> findRecent(Pageable pageable);

    /** Whether a review from this customer for this product may be flagged "verified purchase". */
    @Query("""
            SELECT COUNT(o) > 0 FROM Order o JOIN o.items i
            WHERE o.customer.id = :customerId AND i.product.id = :productId
              AND o.status IN (az.catalog.modules.commerce.order.OrderStatus.DELIVERED,
                               az.catalog.modules.commerce.order.OrderStatus.COMPLETED)
            """)
    boolean existsDeliveredOrderForCustomerAndProduct(
            @Param("customerId") Long customerId, @Param("productId") Long productId);
}
