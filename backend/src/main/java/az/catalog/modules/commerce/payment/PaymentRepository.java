package az.catalog.modules.commerce.payment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByReference(String reference);

    Optional<Payment> findByExternalTransactionId(String externalTransactionId);

    List<Payment> findAllByOrderIdOrderByCreatedAtDesc(Long orderId);

    /** The payment a shopper is currently in the middle of, if any. */
    @Query("""
            SELECT p FROM Payment p
            WHERE p.orderId = :orderId
              AND p.status IN (az.catalog.modules.commerce.payment.Payment$Status.CREATED,
                               az.catalog.modules.commerce.payment.Payment$Status.PENDING,
                               az.catalog.modules.commerce.payment.Payment$Status.AUTHORIZED)
            ORDER BY p.createdAt DESC
            """)
    List<Payment> findOpenForOrder(@Param("orderId") Long orderId);

    @Query("""
            SELECT p FROM Payment p
            WHERE (:status IS NULL OR p.status = :status)
              AND (:search IS NULL OR lower(p.reference) LIKE lower(concat('%', :search, '%'))
                                   OR lower(p.externalTransactionId) LIKE lower(concat('%', :search, '%')))
            """)
    Page<Payment> search(@Param("search") String search,
                         @Param("status") Payment.Status status,
                         Pageable pageable);
}
