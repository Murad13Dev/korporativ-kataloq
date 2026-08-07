package az.catalog.modules.page;

import az.catalog.common.config.CacheConfig;
import az.catalog.common.exception.ApiException;
import az.catalog.modules.media.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Storefront page reads.
 *
 * Kept apart from {@link PageService} because the shapes differ in an important way:
 * the admin needs every field of every section including hidden ones, while the public
 * renderer must never see a hidden section at all. Filtering in one place means a
 * forgetful caller cannot leak unpublished content.
 */
@Service
@RequiredArgsConstructor
public class PublicPageService {

    private final PageVersionRepository versionRepository;
    private final MediaService mediaService;

    @Cacheable(value = CacheConfig.PUBLIC_PAGE, key = "'slug:' + #slug + ':' + #language")
    @Transactional(readOnly = true)
    public PageDtos.PublicPage bySlug(String slug, String language) {
        PageVersion version = versionRepository.findPublishedBySlug(slug, language)
                .orElseThrow(() -> ApiException.notFoundBySlug("Səhifə", slug));
        return toPublic(version);
    }

    @Cacheable(value = CacheConfig.PUBLIC_PAGE, key = "'code:' + #code + ':' + #language")
    @Transactional(readOnly = true)
    public PageDtos.PublicPage byCode(String code, String language) {
        PageVersion version = versionRepository.findPublishedByPageCode(code.toUpperCase(), language)
                .orElseThrow(() -> ApiException.notFound("Səhifə", code));
        return toPublic(version);
    }

    private PageDtos.PublicPage toPublic(PageVersion version) {
        List<PageSection> visible = version.getSections().stream()
                .filter(PageSection::isActive)
                .sorted(Comparator.comparingInt(PageSection::getSortOrder))
                .toList();

        Map<Long, String> mediaUrls = mediaService.publicUrls(mediaIds(version, visible));
        Map<Long, Boolean> videoFlags = mediaService.videoFlags(mediaIds(version, visible));

        List<PageDtos.PublicSection> sections = visible.stream()
                .map(section -> new PageDtos.PublicSection(
                        section.getId(), section.getSectionType(), section.getVariant(),
                        section.getEyebrow(), section.getTitle(), section.getSubtitle(),
                        section.getContent(),
                        mediaUrls.get(section.getMediaId()),
                        Boolean.TRUE.equals(videoFlags.get(section.getMediaId())),
                        mediaUrls.get(section.getBackgroundMediaId()),
                        Boolean.TRUE.equals(videoFlags.get(section.getBackgroundMediaId())),
                        section.getButtonText(), section.getButtonUrl(),
                        section.getSecondaryButtonText(), section.getSecondaryButtonUrl(),
                        section.getBackgroundStyle(), section.getConfiguration()))
                .toList();

        return new PageDtos.PublicPage(
                version.getPage().getCode(), version.getSlug(), version.getTitle(),
                version.getShortDescription(),
                mediaUrls.get(version.getCoverMediaId()),
                // SEO fields fall back to the visible content rather than emitting an
                // empty <title>, which is worse than a duplicated one.
                version.getSeoTitle() != null && !version.getSeoTitle().isBlank()
                        ? version.getSeoTitle() : version.getTitle(),
                version.getSeoDescription() != null && !version.getSeoDescription().isBlank()
                        ? version.getSeoDescription() : version.getShortDescription(),
                version.getCanonicalUrl(), version.isAllowIndexing(),
                mediaUrls.get(version.getOgMediaId()),
                version.getPage().getLayout(),
                sections);
    }

    private List<Long> mediaIds(PageVersion version, List<PageSection> sections) {
        List<Long> ids = new ArrayList<>();
        ids.add(version.getCoverMediaId());
        ids.add(version.getOgMediaId());
        sections.forEach(section -> {
            ids.add(section.getMediaId());
            ids.add(section.getBackgroundMediaId());
        });
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}
