package az.catalog.modules.dashboard;

import az.catalog.modules.language.Language;
import az.catalog.modules.language.LanguageService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * A white-label install ships every active language, but nothing forces an admin to
 * actually fill every translation in — this is the "128 products, 42 missing EN"
 * report nobody else in a small catalog platform bothers to surface. Read-only, four
 * plain counting queries per language: cheap enough to run on every dashboard load
 * without its own caching layer.
 */
@Service
@RequiredArgsConstructor
public class TranslationCoverageService {

    private final JdbcTemplate jdbcTemplate;
    private final LanguageService languageService;

    @Transactional(readOnly = true)
    public List<DashboardDtos.LanguageCoverage> coverage() {
        String defaultLanguage = languageService.defaultLanguageCode();
        List<Language> languages = languageService.activeLanguages();

        return languages.stream()
                // The default language is the source of truth by definition — reporting
                // "100% translated into itself" would just be noise on every dashboard load.
                .filter(language -> !language.getCode().equals(defaultLanguage))
                .map(language -> toCoverage(language, defaultLanguage))
                .toList();
    }

    private DashboardDtos.LanguageCoverage toCoverage(Language language, String defaultLanguage) {
        String lang = language.getCode();

        Long[] counts = jdbcTemplate.query("""
                SELECT
                    (SELECT COUNT(*) FROM products WHERE deleted_at IS NULL AND status = 'ACTIVE') AS products_total,
                    (SELECT COUNT(*) FROM products p
                        JOIN product_translations pt ON pt.product_id = p.id AND pt.language_code = ?
                        WHERE p.deleted_at IS NULL AND p.status = 'ACTIVE') AS products_done,
                    (SELECT COUNT(*) FROM categories WHERE deleted_at IS NULL AND status = 'ACTIVE') AS categories_total,
                    (SELECT COUNT(*) FROM categories c
                        JOIN category_translations ct ON ct.category_id = c.id AND ct.language_code = ?
                        WHERE c.deleted_at IS NULL AND c.status = 'ACTIVE') AS categories_done,
                    (SELECT COUNT(*) FROM posts WHERE deleted_at IS NULL AND status = 'ACTIVE') AS posts_total,
                    (SELECT COUNT(*) FROM posts p
                        JOIN post_translations pt ON pt.post_id = p.id AND pt.language_code = ?
                        WHERE p.deleted_at IS NULL AND p.status = 'ACTIVE') AS posts_done,
                    (SELECT COUNT(DISTINCT page_id) FROM page_versions
                        WHERE language_code = ? AND status = 'PUBLISHED') AS pages_total,
                    (SELECT COUNT(DISTINCT page_id) FROM page_versions
                        WHERE language_code = ? AND status = 'PUBLISHED') AS pages_done
                """,
                (rs, rowNum) -> new Long[]{
                        rs.getLong("products_total"), rs.getLong("products_done"),
                        rs.getLong("categories_total"), rs.getLong("categories_done"),
                        rs.getLong("posts_total"), rs.getLong("posts_done"),
                        rs.getLong("pages_total"), rs.getLong("pages_done")},
                lang, lang, lang, defaultLanguage, lang
        ).get(0);

        return new DashboardDtos.LanguageCoverage(
                lang, language.getName(),
                new DashboardDtos.ContentCoverage(counts[0], counts[1]),
                new DashboardDtos.ContentCoverage(counts[2], counts[3]),
                new DashboardDtos.ContentCoverage(counts[4], counts[5]),
                new DashboardDtos.ContentCoverage(counts[6], counts[7]));
    }
}
