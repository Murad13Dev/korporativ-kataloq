package az.catalog.modules.page;

import az.catalog.common.config.CacheConfig;
import az.catalog.common.domain.VersionStatus;
import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.common.util.HtmlSanitizer;
import az.catalog.common.util.SlugUtil;
import az.catalog.modules.language.LanguageService;
import az.catalog.modules.media.MediaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Pages and their sections.
 *
 * The lifecycle mirrors site configuration: edits go to a DRAFT, publishing flips it to
 * PUBLISHED and archives the previous live version. Partial unique indexes guarantee at
 * most one DRAFT and one PUBLISHED per (page, language), so two editors racing cannot
 * produce two live pages.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PageService {

    private final PageRepository pageRepository;
    private final PageVersionRepository versionRepository;
    private final LanguageService languageService;
    private final MediaService mediaService;
    private final HtmlSanitizer sanitizer;
    private final ObjectMapper objectMapper;

    // =======================================================================
    // Pages
    // =======================================================================

    @Transactional(readOnly = true)
    public List<PageDtos.PageRow> list() {
        return pageRepository.findAllAlive().stream()
                .map(page -> new PageDtos.PageRow(
                        page.getId(), page.getCode(), page.getPageType(), page.isSystem(),
                        page.getLayout(), page.getSortOrder(),
                        languageStates(page.getId()), page.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageDtos.PageResponse get(Long id) {
        Page page = loadPage(id);
        return new PageDtos.PageResponse(
                page.getId(), page.getCode(), page.getPageType(), page.isSystem(),
                page.getLayout(), page.getSortOrder(), languageStates(page.getId()));
    }

    @Transactional
    public PageDtos.PageResponse create(PageDtos.CreatePageRequest request) {
        String code = request.code().trim().toUpperCase();
        if (pageRepository.existsByCode(code, null)) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "Bu kod artıq mövcuddur: " + code);
        }
        languageService.requireActive(request.languageCode());

        Page page = pageRepository.save(Page.builder()
                .code(code)
                .pageType(request.pageType())
                .layout(request.layout() == null || request.layout().isBlank() ? "DEFAULT" : request.layout())
                .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
                .system(false)
                .build());

        // A page with no version is unreachable, so the first draft is created with it.
        PageVersion version = PageVersion.builder()
                .page(page)
                .versionNumber(1)
                .languageCode(request.languageCode())
                .title(sanitizer.stripTags(request.title()).trim())
                .slug(resolveSlug(request.slug(), request.title(), request.languageCode(), page.getId()))
                .status(VersionStatus.DRAFT)
                .build();
        versionRepository.save(version);

        log.info("Created page {} with draft version in {}", code, request.languageCode());
        return get(page.getId());
    }

    @Transactional
    public PageDtos.PageResponse update(Long id, PageDtos.UpdatePageRequest request) {
        Page page = loadPage(id);
        // A system page's type is what the router keys on; changing it would break /elaqe.
        if (!page.isSystem()) {
            page.setPageType(request.pageType());
        }
        if (request.layout() != null && !request.layout().isBlank()) {
            page.setLayout(request.layout());
        }
        if (request.sortOrder() != null) {
            page.setSortOrder(request.sortOrder());
        }
        return get(id);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.PUBLIC_PAGE, allEntries = true)
    public void delete(Long id) {
        Page page = loadPage(id);
        if (page.isSystem()) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE,
                    "Sistem səhifəsi silinə bilməz. Onu gizlətmək üçün versiyanı arxivləyin.");
        }
        page.softDelete();
        log.info("Soft deleted page {}", page.getCode());
    }

    // =======================================================================
    // Versions
    // =======================================================================

    @Transactional(readOnly = true)
    public PageDtos.VersionResponse version(Long versionId) {
        return toVersionResponse(loadVersion(versionId));
    }

    /**
     * The version the editor should open for a language: the open draft if there is one,
     * otherwise the published version.
     */
    @Transactional(readOnly = true)
    public PageDtos.VersionResponse editableVersion(Long pageId, String language) {
        return versionRepository.findByPageAndLanguageAndStatus(pageId, language, VersionStatus.DRAFT)
                .or(() -> versionRepository.findByPageAndLanguageAndStatus(
                        pageId, language, VersionStatus.PUBLISHED))
                .map(this::toVersionResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Bu dil üçün versiya yoxdur: " + language));
    }

    /**
     * Opens a draft, copied from the published version (or an explicitly chosen one) so
     * the editor starts from what is live rather than from a blank page.
     */
    @Transactional
    public PageDtos.VersionResponse createDraft(Long pageId, PageDtos.CreateVersionRequest request) {
        Page page = loadPage(pageId);
        String language = request.languageCode();
        languageService.requireActive(language);

        var existingDraft = versionRepository.findByPageAndLanguageAndStatus(
                pageId, language, VersionStatus.DRAFT);
        if (existingDraft.isPresent()) {
            return toVersionResponse(existingDraft.get());
        }

        PageVersion source = request.sourceVersionId() != null
                ? loadVersion(request.sourceVersionId())
                : versionRepository.findByPageAndLanguageAndStatus(
                        pageId, language, VersionStatus.PUBLISHED).orElse(null);

        PageVersion draft = PageVersion.builder()
                .page(page)
                .versionNumber(versionRepository.maxVersionNumber(pageId, language) + 1)
                .languageCode(language)
                .status(VersionStatus.DRAFT)
                .build();

        if (source != null) {
            draft.setTitle(request.title() != null && !request.title().isBlank()
                    ? request.title() : source.getTitle());
            draft.setSlug(source.getSlug());
            draft.setShortDescription(source.getShortDescription());
            draft.setCoverMediaId(source.getCoverMediaId());
            draft.setSeoTitle(source.getSeoTitle());
            draft.setSeoDescription(source.getSeoDescription());
            draft.setSeoKeywords(source.getSeoKeywords());
            draft.setOgMediaId(source.getOgMediaId());
            draft.setCanonicalUrl(source.getCanonicalUrl());
            draft.setAllowIndexing(source.isAllowIndexing());
            source.getSections().forEach(section -> draft.addSection(section.copy()));
        } else {
            String title = request.title() != null && !request.title().isBlank()
                    ? request.title() : page.getCode();
            draft.setTitle(sanitizer.stripTags(title).trim());
            draft.setSlug(resolveSlug(request.slug(), title, language, pageId));
        }

        PageVersion saved = versionRepository.save(draft);
        log.info("Opened draft v{} for page {} ({})", saved.getVersionNumber(), page.getCode(), language);
        return toVersionResponse(saved);
    }

    @Transactional
    public PageDtos.VersionResponse updateDraft(Long versionId, PageDtos.UpdateVersionRequest request) {
        PageVersion version = loadVersion(versionId);
        requireEditable(version);

        version.setTitle(sanitizer.stripTags(request.title()).trim());
        if (request.slug() != null && !request.slug().isBlank()) {
            version.setSlug(resolveSlug(request.slug(), request.title(),
                    version.getLanguageCode(), version.getPage().getId()));
        }
        version.setShortDescription(sanitizer.stripTags(request.shortDescription()));
        version.setCoverMediaId(request.coverMediaId());
        version.setSeoTitle(sanitizer.stripTags(request.seoTitle()));
        version.setSeoDescription(sanitizer.stripTags(request.seoDescription()));
        version.setSeoKeywords(sanitizer.stripTags(request.seoKeywords()));
        version.setOgMediaId(request.ogMediaId());
        version.setCanonicalUrl(request.canonicalUrl());
        if (request.allowIndexing() != null) {
            version.setAllowIndexing(request.allowIndexing());
        }

        return toVersionResponse(version);
    }

    /**
     * Publishes a draft. The previously live version is archived first and flushed,
     * because the partial unique index allows only one PUBLISHED row per (page, language).
     */
    @Transactional
    @CacheEvict(value = CacheConfig.PUBLIC_PAGE, allEntries = true)
    public PageDtos.VersionResponse publish(Long versionId) {
        PageVersion draft = loadVersion(versionId);

        if (draft.getStatus() != VersionStatus.DRAFT) {
            throw new ApiException(ErrorCode.VERSION_NOT_PUBLISHABLE,
                    "Yalnız draft versiya dərc edilə bilər");
        }
        if (draft.getTitle() == null || draft.getTitle().isBlank()) {
            throw ApiException.field(ErrorCode.VALIDATION_ERROR, "title", "Başlıq boş ola bilməz");
        }
        if (versionRepository.slugTakenByAnotherPage(
                draft.getSlug(), draft.getLanguageCode(), draft.getPage().getId())) {
            throw new ApiException(ErrorCode.SLUG_ALREADY_EXISTS,
                    "Bu slug başqa səhifə tərəfindən istifadə olunur: /" + draft.getSlug());
        }

        versionRepository.findByPageAndLanguageAndStatus(
                        draft.getPage().getId(), draft.getLanguageCode(), VersionStatus.PUBLISHED)
                .ifPresent(current -> current.setStatus(VersionStatus.ARCHIVED));
        versionRepository.flush();

        draft.setStatus(VersionStatus.PUBLISHED);
        draft.setPublishedAt(OffsetDateTime.now());

        log.info("Published page {} v{} ({})", draft.getPage().getCode(),
                draft.getVersionNumber(), draft.getLanguageCode());
        return toVersionResponse(draft);
    }

    /** Discards an open draft. The live page is untouched. */
    @Transactional
    public void discardDraft(Long versionId) {
        PageVersion version = loadVersion(versionId);
        if (version.getStatus() != VersionStatus.DRAFT) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE, "Yalnız draft versiya ləğv edilə bilər");
        }
        versionRepository.delete(version);
    }

    /** Copies an archived version into a new draft; it never publishes directly. */
    @Transactional
    public PageDtos.VersionResponse restore(Long versionId) {
        PageVersion source = loadVersion(versionId);
        if (source.getStatus() == VersionStatus.DRAFT) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE, "Draft versiya bərpa edilə bilməz");
        }

        versionRepository.findByPageAndLanguageAndStatus(
                        source.getPage().getId(), source.getLanguageCode(), VersionStatus.DRAFT)
                .ifPresent(versionRepository::delete);
        versionRepository.flush();

        return createDraft(source.getPage().getId(), new PageDtos.CreateVersionRequest(
                source.getLanguageCode(), source.getId(), source.getTitle(), source.getSlug()));
    }

    @Transactional(readOnly = true)
    public List<PageDtos.VersionResponse> versions(Long pageId) {
        return versionRepository.findAllForPage(pageId).stream()
                .map(this::toVersionSummary)
                .toList();
    }

    // =======================================================================
    // Sections
    // =======================================================================

    @Transactional
    public PageDtos.VersionResponse addSection(Long versionId, PageDtos.SaveSectionRequest request) {
        PageVersion version = loadVersion(versionId);
        requireEditable(version);

        PageSection section = new PageSection();
        section.setSectionType(request.sectionType());
        applySection(section, request);
        section.setSortOrder(request.sortOrder() == null
                ? version.getSections().size() : request.sortOrder());

        version.addSection(section);
        return toVersionResponse(version);
    }

    @Transactional
    public PageDtos.VersionResponse updateSection(Long versionId, Long sectionId,
                                                  PageDtos.SaveSectionRequest request) {
        PageVersion version = loadVersion(versionId);
        requireEditable(version);

        PageSection section = findSection(version, sectionId);
        // The type decides which fields mean anything, so changing it mid-edit would
        // leave stale values in columns the new type never reads.
        if (section.getSectionType() != request.sectionType()) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE,
                    "Bölmə tipi dəyişdirilə bilməz. Bölməni silib yenisini əlavə edin.");
        }
        applySection(section, request);
        return toVersionResponse(version);
    }

    @Transactional
    public PageDtos.VersionResponse removeSection(Long versionId, Long sectionId) {
        PageVersion version = loadVersion(versionId);
        requireEditable(version);
        version.getSections().removeIf(section -> section.getId().equals(sectionId));
        return toVersionResponse(version);
    }

    @Transactional
    public PageDtos.VersionResponse setSectionActive(Long versionId, Long sectionId, boolean active) {
        PageVersion version = loadVersion(versionId);
        requireEditable(version);
        findSection(version, sectionId).setActive(active);
        return toVersionResponse(version);
    }

    @Transactional
    public PageDtos.VersionResponse reorderSections(Long versionId, PageDtos.ReorderRequest request) {
        PageVersion version = loadVersion(versionId);
        requireEditable(version);

        Map<Long, Integer> order = request.items().stream()
                .collect(Collectors.toMap(PageDtos.ReorderItem::id, PageDtos.ReorderItem::sortOrder));
        version.getSections().forEach(section ->
                section.setSortOrder(order.getOrDefault(section.getId(), section.getSortOrder())));
        version.getSections().sort(Comparator.comparingInt(PageSection::getSortOrder));

        return toVersionResponse(version);
    }

    /** Duplicates a section in place — the fastest way to build a repeating layout. */
    @Transactional
    public PageDtos.VersionResponse duplicateSection(Long versionId, Long sectionId) {
        PageVersion version = loadVersion(versionId);
        requireEditable(version);

        PageSection source = findSection(version, sectionId);
        PageSection copy = source.copy();
        copy.setSortOrder(source.getSortOrder() + 1);

        version.getSections().stream()
                .filter(section -> section.getSortOrder() > source.getSortOrder())
                .forEach(section -> section.setSortOrder(section.getSortOrder() + 1));

        version.addSection(copy);
        return toVersionResponse(version);
    }

    @Transactional(readOnly = true)
    public List<PageDtos.SectionTypeInfo> sectionRegistry() {
        return java.util.Arrays.stream(SectionType.values())
                .map(PageDtos.SectionTypeInfo::from)
                .toList();
    }

    // =======================================================================
    // Internals
    // =======================================================================

    private void applySection(PageSection section, PageDtos.SaveSectionRequest request) {
        SectionType type = request.sectionType();

        section.setVariant(type.resolveVariant(request.variant()));

        // Only fields the type actually declares are stored; the rest are nulled so a
        // section switched between variants cannot carry invisible leftovers.
        section.setEyebrow(type.uses(SectionType.Field.EYEBROW)
                ? sanitizer.stripTags(request.eyebrow()) : null);
        section.setTitle(type.uses(SectionType.Field.TITLE)
                ? sanitizer.stripTags(request.title()) : null);
        section.setSubtitle(type.uses(SectionType.Field.SUBTITLE)
                ? sanitizer.stripTags(request.subtitle()) : null);
        section.setContent(type.uses(SectionType.Field.CONTENT)
                ? sanitizer.sanitizeRichText(request.content()) : null);
        section.setMediaId(type.uses(SectionType.Field.MEDIA) ? request.mediaId() : null);
        section.setBackgroundMediaId(type.uses(SectionType.Field.BACKGROUND_MEDIA)
                ? request.backgroundMediaId() : null);

        if (type.uses(SectionType.Field.BUTTON)) {
            section.setButtonText(sanitizer.stripTags(request.buttonText()));
            section.setButtonUrl(safeUrl(request.buttonUrl()));
        } else {
            section.setButtonText(null);
            section.setButtonUrl(null);
        }

        if (type.uses(SectionType.Field.SECONDARY_BUTTON)) {
            section.setSecondaryButtonText(sanitizer.stripTags(request.secondaryButtonText()));
            section.setSecondaryButtonUrl(safeUrl(request.secondaryButtonUrl()));
        } else {
            section.setSecondaryButtonText(null);
            section.setSecondaryButtonUrl(null);
        }

        section.setBackgroundStyle(request.backgroundStyle() == null
                ? PageSection.BackgroundStyle.DEFAULT : request.backgroundStyle());
        section.setConfiguration(request.configuration() == null
                ? objectMapper.createObjectNode() : request.configuration());

        if (request.sortOrder() != null) {
            section.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            section.setActive(request.active());
        }
    }

    /**
     * Button targets are admin-supplied and end up in an {@code href}, so anything that
     * is not a relative path, http(s), mailto or tel is dropped.
     */
    private String safeUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase();
        boolean allowed = trimmed.startsWith("/")
                || trimmed.startsWith("#")
                || lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("mailto:")
                || lower.startsWith("tel:");
        if (!allowed) {
            throw ApiException.field(ErrorCode.VALIDATION_ERROR, "buttonUrl",
                    "Link nisbi yol (/…) və ya http, https, mailto, tel olmalıdır");
        }
        return trimmed;
    }

    private String resolveSlug(String requested, String fallbackSource, String language, Long pageId) {
        String base = requested != null && !requested.isBlank() ? requested : fallbackSource;
        String slug = SlugUtil.slugify(base);
        if (slug.isEmpty()) {
            slug = "sehife";
        }
        if (!SlugUtil.isValidSlug(slug)) {
            throw ApiException.field(ErrorCode.VALIDATION_ERROR, "slug", "Slug formatı yanlışdır");
        }
        // Uniqueness is only enforced against *published* pages: a draft may hold the
        // slug it is about to take over.
        if (pageId != null && versionRepository.slugTakenByAnotherPage(slug, language, pageId)) {
            throw new ApiException(ErrorCode.SLUG_ALREADY_EXISTS);
        }
        return slug;
    }

    private List<PageDtos.LanguageState> languageStates(Long pageId) {
        List<PageVersion> versions = versionRepository.findAllForPage(pageId);

        Map<String, List<PageVersion>> byLanguage = versions.stream()
                .collect(Collectors.groupingBy(PageVersion::getLanguageCode,
                        LinkedHashMap::new, Collectors.toList()));

        List<PageDtos.LanguageState> states = new ArrayList<>();
        languageService.activeLanguageCodes().forEach(code -> {
            List<PageVersion> forLanguage = byLanguage.getOrDefault(code, List.of());
            if (forLanguage.isEmpty()) {
                return;
            }
            PageVersion published = forLanguage.stream()
                    .filter(PageVersion::isPublished).findFirst().orElse(null);
            PageVersion draft = forLanguage.stream()
                    .filter(version -> version.getStatus() == VersionStatus.DRAFT)
                    .findFirst().orElse(null);
            PageVersion display = published != null ? published : draft;

            states.add(new PageDtos.LanguageState(
                    code,
                    display == null ? null : display.getTitle(),
                    display == null ? null : display.getSlug(),
                    published != null,
                    draft != null,
                    published == null ? null : published.getPublishedAt()));
        });
        return states;
    }

    private PageDtos.VersionResponse toVersionResponse(PageVersion version) {
        Map<Long, String> mediaUrls = mediaService.publicUrls(collectMediaIds(version));

        List<PageDtos.SectionResponse> sections = version.getSections().stream()
                .sorted(Comparator.comparingInt(PageSection::getSortOrder))
                .map(section -> new PageDtos.SectionResponse(
                        section.getId(), section.getSectionType(),
                        section.getSectionType().label(), section.getVariant(),
                        section.getSectionType().variants(),
                        section.getSectionType().fields().stream().map(Enum::name).toList(),
                        section.getEyebrow(), section.getTitle(), section.getSubtitle(),
                        section.getContent(), section.getMediaId(),
                        mediaUrls.get(section.getMediaId()),
                        section.getBackgroundMediaId(),
                        mediaUrls.get(section.getBackgroundMediaId()),
                        section.getButtonText(), section.getButtonUrl(),
                        section.getSecondaryButtonText(), section.getSecondaryButtonUrl(),
                        section.getBackgroundStyle(), section.getConfiguration(),
                        section.getSortOrder(), section.isActive()))
                .toList();

        return new PageDtos.VersionResponse(
                version.getId(), version.getPage().getId(), version.getPage().getCode(),
                version.getVersionNumber(), version.getLanguageCode(), version.getSlug(),
                version.getTitle(), version.getShortDescription(), version.getCoverMediaId(),
                mediaUrls.get(version.getCoverMediaId()),
                version.getSeoTitle(), version.getSeoDescription(), version.getSeoKeywords(),
                version.getOgMediaId(), version.getCanonicalUrl(), version.isAllowIndexing(),
                version.getStatus(), version.getPublishedAt(), version.getUpdatedAt(), sections);
    }

    /** Version list entry without the (potentially large) section payload. */
    private PageDtos.VersionResponse toVersionSummary(PageVersion version) {
        return new PageDtos.VersionResponse(
                version.getId(), version.getPage().getId(), version.getPage().getCode(),
                version.getVersionNumber(), version.getLanguageCode(), version.getSlug(),
                version.getTitle(), version.getShortDescription(), version.getCoverMediaId(), null,
                version.getSeoTitle(), version.getSeoDescription(), version.getSeoKeywords(),
                version.getOgMediaId(), version.getCanonicalUrl(), version.isAllowIndexing(),
                version.getStatus(), version.getPublishedAt(), version.getUpdatedAt(), List.of());
    }

    private List<Long> collectMediaIds(PageVersion version) {
        List<Long> ids = new ArrayList<>();
        ids.add(version.getCoverMediaId());
        ids.add(version.getOgMediaId());
        version.getSections().forEach(section -> {
            ids.add(section.getMediaId());
            ids.add(section.getBackgroundMediaId());
        });
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private void requireEditable(PageVersion version) {
        if (version.getStatus() != VersionStatus.DRAFT) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE,
                    "Yalnız draft versiya redaktə edilə bilər. Əvvəlcə yeni draft yaradın.");
        }
    }

    private PageSection findSection(PageVersion version, Long sectionId) {
        return version.getSections().stream()
                .filter(section -> section.getId().equals(sectionId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Bölmə", sectionId));
    }

    Page loadPage(Long id) {
        return pageRepository.findAliveById(id)
                .orElseThrow(() -> ApiException.notFound("Səhifə", id));
    }

    PageVersion loadVersion(Long id) {
        return versionRepository.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Səhifə versiyası", id));
    }
}
