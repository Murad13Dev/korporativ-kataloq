package az.catalog.modules.commerce.cart;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    /**
     * Only {@code items} and {@code items.product} are join-fetched. Both {@code images} and
     * {@code translations} were tried here at various points and both cause the same failure:
     * {@code items} is a {@code List} (bag), and joining ANY second collection alongside it
     * (whether another bag or a per-language map) fans the SQL result out to one row per
     * (cart_item × other-collection-row) pair — a product with 2 languages duplicated every
     * line of the cart. {@code SELECT DISTINCT} does not reliably fix this for
     * {@code @EntityGraph}-driven fetches in this Hibernate version; the join simply isn't
     * created. {@code product.translations} lazy-loads instead, batched by
     * {@code default_batch_fetch_size} (see application.yml) — a handful of extra
     * {@code IN (...)} queries per cart, not one per line.
     */
    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("""
            SELECT c FROM Cart c
            WHERE c.customer.id = :customerId AND c.status = az.catalog.modules.commerce.cart.Cart$Status.ACTIVE
            """)
    Optional<Cart> findActiveByCustomer(@Param("customerId") Long customerId);

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("""
            SELECT c FROM Cart c
            WHERE c.sessionId = :sessionId AND c.status = az.catalog.modules.commerce.cart.Cart$Status.ACTIVE
            """)
    Optional<Cart> findActiveBySession(@Param("sessionId") String sessionId);

    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Cart> findByIdAndStatus(Long id, Cart.Status status);

    /** Carts untouched for a while are marked abandoned so the dashboard can report on them. */
    @Modifying
    @Query("""
            UPDATE Cart c SET c.status = az.catalog.modules.commerce.cart.Cart$Status.ABANDONED
            WHERE c.status = az.catalog.modules.commerce.cart.Cart$Status.ACTIVE
              AND c.updatedAt < :cutoff
            """)
    int markAbandoned(@Param("cutoff") OffsetDateTime cutoff);

    @Query("""
            SELECT c FROM Cart c
            WHERE c.status = az.catalog.modules.commerce.cart.Cart$Status.ABANDONED
              AND c.updatedAt >= :since
            """)
    List<Cart> findRecentlyAbandoned(@Param("since") OffsetDateTime since);

    @Query("""
            SELECT COUNT(c) FROM Cart c
            WHERE c.status = az.catalog.modules.commerce.cart.Cart$Status.ACTIVE
              AND size(c.items) > 0
            """)
    long countActiveNonEmpty();

    /**
     * Candidates for the "did you forget something?" e-mail — scoped to known customers
     * only (a guest cart has no address to mail), untouched for a while, never reminded.
     * Deliberately independent of {@link #markAbandoned}: that job runs on a 30-day
     * housekeeping horizon, this one on an hours-long commercial-recovery horizon, and
     * conflating the two would mean either a reminder a month late or a report that
     * calls a 4-hour-old cart "abandoned".
     */
    @EntityGraph(attributePaths = {"items", "items.product", "customer"})
    @Query("""
            SELECT c FROM Cart c
            WHERE c.status = az.catalog.modules.commerce.cart.Cart$Status.ACTIVE
              AND c.customer IS NOT NULL
              AND size(c.items) > 0
              AND c.updatedAt < :cutoff
              AND c.abandonedReminderSentAt IS NULL
            """)
    List<Cart> findNeedingAbandonmentReminder(@Param("cutoff") OffsetDateTime cutoff);
}
