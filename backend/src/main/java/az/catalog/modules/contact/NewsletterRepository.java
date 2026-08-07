package az.catalog.modules.contact;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NewsletterRepository extends JpaRepository<NewsletterSubscription, Long> {

    @Query("SELECT s FROM NewsletterSubscription s WHERE lower(s.email) = lower(:email)")
    Optional<NewsletterSubscription> findByEmail(@Param("email") String email);

    Optional<NewsletterSubscription> findByConfirmToken(String confirmToken);

    @Query("""
            SELECT s FROM NewsletterSubscription s
            WHERE (:status IS NULL OR s.status = :status)
              AND (:search IS NULL OR lower(s.email) LIKE lower(concat('%', :search, '%')))
            ORDER BY s.createdAt DESC
            """)
    Page<NewsletterSubscription> search(@Param("search") String search,
                                        @Param("status") NewsletterSubscription.Status status,
                                        Pageable pageable);

    @Query("SELECT COUNT(s) FROM NewsletterSubscription s WHERE s.status = :status")
    long countByStatus(@Param("status") NewsletterSubscription.Status status);
}
