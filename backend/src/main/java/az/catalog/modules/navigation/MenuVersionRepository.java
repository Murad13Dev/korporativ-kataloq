package az.catalog.modules.navigation;

import az.catalog.common.domain.VersionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MenuVersionRepository extends JpaRepository<MenuVersion, Long> {

    @EntityGraph(attributePaths = {"items", "items.translations", "menu"})
    @Query("SELECT v FROM MenuVersion v WHERE v.menu.id = :menuId AND v.status = :status")
    Optional<MenuVersion> findByMenuAndStatus(@Param("menuId") Long menuId,
                                              @Param("status") VersionStatus status);

    /** Public read: the live version of whichever menu occupies a location. */
    @EntityGraph(attributePaths = {"items", "items.translations", "menu"})
    @Query("""
            SELECT v FROM MenuVersion v
            WHERE v.menu.location = :location AND v.menu.active = true
              AND v.status = az.catalog.common.domain.VersionStatus.PUBLISHED
            """)
    Optional<MenuVersion> findPublishedByLocation(@Param("location") Menu.Location location);

    @EntityGraph(attributePaths = {"items", "items.translations", "menu"})
    @Query("SELECT v FROM MenuVersion v WHERE v.id = :id")
    Optional<MenuVersion> findDetailById(@Param("id") Long id);

    @Query("SELECT v FROM MenuVersion v WHERE v.menu.id = :menuId ORDER BY v.versionNumber DESC")
    List<MenuVersion> findAllForMenu(@Param("menuId") Long menuId);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM MenuVersion v WHERE v.menu.id = :menuId")
    int maxVersionNumber(@Param("menuId") Long menuId);
}
