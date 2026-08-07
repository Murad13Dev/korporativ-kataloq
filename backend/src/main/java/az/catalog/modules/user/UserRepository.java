package az.catalog.modules.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u WHERE lower(u.email) = lower(:email) AND u.deletedAt IS NULL")
    Optional<User> findActiveByEmail(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<User> findAliveById(@Param("id") Long id);

    @Query("""
            SELECT COUNT(u) > 0 FROM User u
            WHERE lower(u.email) = lower(:email)
              AND u.deletedAt IS NULL
              AND (:excludeId IS NULL OR u.id <> :excludeId)
            """)
    boolean existsByEmail(@Param("email") String email, @Param("excludeId") Long excludeId);

    @Query("""
            SELECT u FROM User u
            WHERE u.deletedAt IS NULL
              AND (:search IS NULL OR lower(u.firstName) LIKE lower(concat('%', :search, '%'))
                                   OR lower(u.lastName) LIKE lower(concat('%', :search, '%'))
                                   OR lower(u.email) LIKE lower(concat('%', :search, '%')))
              AND (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.status = :status)
            """)
    Page<User> search(@Param("search") String search,
                      @Param("role") UserRole role,
                      @Param("status") UserStatus status,
                      Pageable pageable);

    @Query("""
            SELECT COUNT(u) FROM User u
            WHERE u.role = az.catalog.modules.user.UserRole.SUPER_ADMIN
              AND u.status = az.catalog.modules.user.UserStatus.ACTIVE
              AND u.deletedAt IS NULL
              AND (:excludeId IS NULL OR u.id <> :excludeId)
            """)
    long countActiveSuperAdmins(@Param("excludeId") Long excludeId);

    long countByDeletedAtIsNull();
}
