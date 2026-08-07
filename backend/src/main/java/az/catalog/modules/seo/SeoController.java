package az.catalog.modules.seo;

import az.catalog.common.config.AppProperties;
import az.catalog.common.domain.ContentStatus;
import az.catalog.modules.blog.PostRepository;
import az.catalog.modules.catalog.category.CategoryRepository;
import az.catalog.modules.catalog.product.ProductRepository;
import az.catalog.modules.language.LanguageService;
import az.catalog.modules.page.PageVersionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * `sitemap.xml` and `robots.txt`. Nginx maps the bare root paths (`/sitemap.xml`,
 * `/robots.txt`) to these two endpoints — see `frontend/nginx.conf`.
 *
 * The sitemap is assembled from lightweight slug+updatedAt projections rather than full
 * entity fetches: a crawler request touches every published row in the catalog, and
 * nothing here needs more than a URL and a last-modified date.
 */
@Tag(name = "Public · SEO")
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@SecurityRequirements
public class SeoController {

    private static final DateTimeFormatter LAST_MOD = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AppProperties properties;
    private final LanguageService languageService;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PostRepository postRepository;
    private final PageVersionRepository pageVersionRepository;

    @Operation(summary = "XML sitemap")
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        String base = baseUrl();
        String language = languageService.defaultLanguageCode();
        OffsetDateTime now = OffsetDateTime.now();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        appendUrl(xml, base + "/", null, "daily", "1.0");
        appendUrl(xml, base + "/kataloq", null, "daily", "0.9");

        categoryRepository.findAllByStatus(ContentStatus.ACTIVE).forEach(category ->
                appendUrl(xml, base + "/kataloq/" + category.getSlug(), category.getUpdatedAt(), "weekly", "0.8"));

        productRepository.findAllPublishedSlugs().forEach(entry ->
                appendUrl(xml, base + "/mehsul/" + entry.getSlug(), entry.getUpdatedAt(), "weekly", "0.7"));

        appendUrl(xml, base + "/bloq", null, "daily", "0.6");
        postRepository.findAllLiveSlugs(now).forEach(entry ->
                appendUrl(xml, base + "/bloq/" + entry.getSlug(), entry.getUpdatedAt(), "monthly", "0.5"));

        // CMS catch-all pages: one URL per default-language slug. The storefront resolves
        // language by Accept-Language rather than a URL prefix, so a page has exactly one
        // canonical address regardless of which language renders on it.
        pageVersionRepository.findPublishedSlugs(language).forEach(slug ->
                appendUrl(xml, base + "/" + slug, null, "monthly", "0.5"));

        xml.append("</urlset>\n");

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(xml.toString());
    }

    @Operation(summary = "robots.txt")
    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots() {
        String body = """
                User-agent: *
                Disallow: /admin
                Disallow: /hesab
                Disallow: /sebet
                Disallow: /checkout
                Allow: /

                Sitemap: %s/sitemap.xml
                """.formatted(baseUrl());
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(body);
    }

    private String baseUrl() {
        String url = properties.getPublicUrl();
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private void appendUrl(StringBuilder xml, String loc, OffsetDateTime updatedAt,
                           String changeFrequency, String priority) {
        xml.append("  <url>\n");
        xml.append("    <loc>").append(escape(loc)).append("</loc>\n");
        if (updatedAt != null) {
            xml.append("    <lastmod>").append(updatedAt.format(LAST_MOD)).append("</lastmod>\n");
        }
        xml.append("    <changefreq>").append(changeFrequency).append("</changefreq>\n");
        xml.append("    <priority>").append(priority).append("</priority>\n");
        xml.append("  </url>\n");
    }

    private String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
