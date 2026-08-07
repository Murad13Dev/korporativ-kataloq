package az.catalog.modules.page;

import az.catalog.common.domain.VersionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PageVersionRepository extends JpaRepository<PageVersion, Long> {

    @EntityGraph(attributePaths = {"sections", "page"})
    @Query("""
            SELECT v FROM PageVersion v
            WHERE v.page.id = :pageId AND v.languageCode = :language AND v.status = :status
            """)
    Optional<PageVersion> findByPageAndLanguageAndStatus(@Param("pageId") Long pageId,
                                                         @Param("language") String language,
                                                         @Param("status") VersionStatus status);

    /** Public lookup: the live version of a page, addressed by its slug. */
    @EntityGraph(attributePaths = {"sections", "page"})
    @Query("""
            SELECT v FROM PageVersion v
            WHERE v.slug = :slug AND v.languageCode = :language
              AND v.status = az.catalog.common.domain.VersionStatus.PUBLISHED
              AND v.page.deletedAt IS NULL
            """)
    Optional<PageVersion> findPublishedBySlug(@Param("slug") String slug,
                                              @Param("language") String language);

    @EntityGraph(attributePaths = {"sections", "page"})
    @Query("""
            SELECT v FROM PageVersion v
            WHERE v.page.code = :code AND v.languageCode = :language
              AND v.status = az.catalog.common.domain.VersionStatus.PUBLISHED
              AND v.page.deletedAt IS NULL
            """)
    Optional<PageVersion> findPublishedByPageCode(@Param("code") String code,
                                                  @Param("language") String language);

    @EntityGraph(attributePaths = {"sections"})
    @Query("SELECT v FROM PageVersion v WHERE v.id = :id")
    Optional<PageVersion> findDetailById(@Param("id") Long id);

    @Query("""
            SELECT v FROM PageVersion v
            WHERE v.page.id = :pageId
            ORDER BY v.languageCode, v.versionNumber DESC
            """)
    List<PageVersion> findAllForPage(@Param("pageId") Long pageId);

    @Query("""
            SELECT COALESCE(MAX(v.versionNumber), 0) FROM PageVersion v
            WHERE v.page.id = :pageId AND v.languageCode = :language
            """)
    int maxVersionNumber(@Param("pageId") Long pageId, @Param("language") String language);

    /**
     * A slug must identify one live page per language. Draft rows are excluded so an
     * unpublished draft can hold a slug the operator intends to switch to.
     */
    @Query("""
            SELECT COUNT(v) > 0 FROM PageVersion v
            WHERE v.slug = :slug AND v.languageCode = :language
              AND v.status = az.catalog.common.domain.VersionStatus.PUBLISHED
              AND v.page.id <> :excludePageId
              AND v.page.deletedAt IS NULL
            """)
    boolean slugTakenByAnotherPage(@Param("slug") String slug,
                                   @Param("language") String language,
                                   @Param("excludePageId") Long excludePageId);

    /** Languages a page already has any version in — drives the editor's language tabs. */
    @Query("SELECT DISTINCT v.languageCode FROM PageVersion v WHERE v.page.id = :pageId")
    List<String> findLanguagesForPage(@Param("pageId") Long pageId);

    @Query("""
            SELECT v.slug FROM PageVersion v
            WHERE v.languageCode = :language
              AND v.status = az.catalog.common.domain.VersionStatus.PUBLISHED
              AND v.allowIndexing = true
              AND v.page.deletedAt IS NULL
            """)
    List<String> findPublishedSlugs(@Param("language") String language);
}
