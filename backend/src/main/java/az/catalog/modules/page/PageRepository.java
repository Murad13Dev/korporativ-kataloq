package az.catalog.modules.page;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PageRepository extends JpaRepository<Page, Long> {

    @Query("SELECT p FROM Page p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Page> findAliveById(@Param("id") Long id);

    @Query("SELECT p FROM Page p WHERE p.code = :code AND p.deletedAt IS NULL")
    Optional<Page> findAliveByCode(@Param("code") String code);

    @Query("SELECT p FROM Page p WHERE p.deletedAt IS NULL ORDER BY p.sortOrder, p.id")
    List<Page> findAllAlive();

    @Query("""
            SELECT COUNT(p) > 0 FROM Page p
            WHERE p.code = :code AND p.deletedAt IS NULL
              AND (:excludeId IS NULL OR p.id <> :excludeId)
            """)
    boolean existsByCode(@Param("code") String code, @Param("excludeId") Long excludeId);

    long countByDeletedAtIsNull();
}
