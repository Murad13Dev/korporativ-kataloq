package az.catalog.modules.commerce.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query("SELECT c FROM Customer c WHERE lower(c.email) = lower(:email) AND c.deletedAt IS NULL")
    Optional<Customer> findAliveByEmail(@Param("email") String email);

    @Query("SELECT c FROM Customer c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<Customer> findAliveById(@Param("id") Long id);

    @Query("""
            SELECT COUNT(c) > 0 FROM Customer c
            WHERE lower(c.email) = lower(:email) AND c.deletedAt IS NULL
              AND c.passwordHash IS NOT NULL
            """)
    boolean existsRegisteredWithEmail(@Param("email") String email);

    @Query("""
            SELECT c FROM Customer c
            WHERE c.deletedAt IS NULL
              AND (:search IS NULL OR lower(c.firstName) LIKE lower(concat('%', :search, '%'))
                                   OR lower(c.lastName) LIKE lower(concat('%', :search, '%'))
                                   OR lower(c.email) LIKE lower(concat('%', :search, '%'))
                                   OR c.phone LIKE concat('%', :search, '%'))
              AND (:status IS NULL OR c.status = :status)
            """)
    Page<Customer> search(@Param("search") String search,
                          @Param("status") Customer.Status status,
                          Pageable pageable);

    long countByDeletedAtIsNull();

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.deletedAt IS NULL AND c.createdAt >= :since")
    long countCreatedSince(@Param("since") OffsetDateTime since);
}
