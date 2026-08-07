package az.catalog.modules.navigation;

import az.catalog.common.domain.VersionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    Optional<Menu> findByCode(String code);

    @Query("SELECT m FROM Menu m ORDER BY m.location, m.id")
    List<Menu> findAllOrdered();

    @Query("""
            SELECT COUNT(m) > 0 FROM Menu m
            WHERE m.code = :code AND (:excludeId IS NULL OR m.id <> :excludeId)
            """)
    boolean existsByCode(@Param("code") String code, @Param("excludeId") Long excludeId);
}
