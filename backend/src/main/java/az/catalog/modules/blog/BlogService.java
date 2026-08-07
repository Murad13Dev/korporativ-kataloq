package az.catalog.modules.blog;

import az.catalog.common.domain.ContentStatus;
import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.common.util.HtmlSanitizer;
import az.catalog.common.util.SlugUtil;
import az.catalog.modules.blog.BlogTaxonomy.PostCategory;
import az.catalog.modules.blog.BlogTaxonomy.PostCategoryTranslation;
import az.catalog.modules.blog.BlogTaxonomy.Tag;
import az.catalog.modules.blog.BlogTaxonomy.TagTranslation;
import az.catalog.modules.language.LanguageService;
import az.catalog.modules.media.MediaService;
import az.catalog.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Blog posts, categories and tags.
 *
 * Publication is a status plus an instant rather than a separate flag: an ACTIVE post
 * whose {@code publishedAt} lies in the future is scheduled, and every public query
 * filters on both. That means scheduling needs no cron job — the post simply becomes
 * visible when the clock passes it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogService {

    /** Average silent reading speed, used to estimate "N dəq oxunuş". */
    private static final int WORDS_PER_MINUTE = 200;

    private final PostRepository postRepository;
    private final PostCategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final LanguageService languageService;
    private final MediaService mediaService;
    private final HtmlSanitizer sanitizer;

    // =======================================================================
    // Public reads
    // =======================================================================

    @Transactional(readOnly = true)
    public Page<BlogDtos.PostCard> publicList(String categorySlug, String tagSlug, String search,
                                              String language, Pageable pageable) {
        Page<Post> page = postRepository.findLive(
                OffsetDateTime.now(), blankToNull(categorySlug), blankToNull(tagSlug),
                blankToNull(search), language, pageable);
        return page.map(post -> toCard(post, language, coverUrls(page.getContent())));
    }

    @Transactional
    public BlogDtos.PostDetail publicDetail(String slug, String language) {
        Post post = postRepository.findLiveBySlug(slug, OffsetDateTime.now())
                .orElseThrow(() -> ApiException.notFoundBySlug("Bloq yazısı", slug));

        postRepository.incrementViewCount(post.getId());
        return toDetail(post, language);
    }

    @Transactional(readOnly = true)
    public List<BlogDtos.PostCard> featured(String language, int limit) {
        List<Post> posts = postRepository.findFeatured(
                OffsetDateTime.now(), PageRequest.of(0, Math.min(limit, 24)));
        Map<Long, String> covers = coverUrls(posts);
        return posts.stream().map(post -> toCard(post, language, covers)).toList();
    }

    /** "Read next" rail: same category first, topped up with recent posts. */
    @Transactional(readOnly = true)
    public List<BlogDtos.PostCard> related(String slug, String language, int limit) {
        Post post = postRepository.findLiveBySlug(slug, OffsetDateTime.now())
                .orElseThrow(() -> ApiException.notFoundBySlug("Bloq yazısı", slug));

        int capped = Math.min(limit, 12);
        List<Post> related = new java.util.ArrayList<>(postRepository.findRelated(
                OffsetDateTime.now(),
                post.getCategory() == null ? null : post.getCategory().getId(),
                post.getId(), PageRequest.of(0, capped)));

        if (related.size() < capped) {
            Set<Long> seen = related.stream().map(Post::getId).collect(java.util.stream.Collectors.toSet());
            seen.add(post.getId());
            postRepository.findRelated(OffsetDateTime.now(), null, post.getId(),
                            PageRequest.of(0, capped)).stream()
                    .filter(candidate -> !seen.contains(candidate.getId()))
                    .limit(capped - related.size())
                    .forEach(related::add);
        }

        Map<Long, String> covers = coverUrls(related);
        return related.stream().map(candidate -> toCard(candidate, language, covers)).toList();
    }

    @Transactional(readOnly = true)
    public List<BlogDtos.CategoryDto> publicCategories(String language) {
        String fallback = languageService.defaultLanguageCode();
        return categoryRepository.findActive().stream()
                .map(category -> new BlogDtos.CategoryDto(
                        category.getId(), category.getSlug(),
                        category.name(language, fallback),
                        descriptionOf(category, language, fallback),
                        category.getColor(),
                        postRepository.countInCategory(category.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BlogDtos.TagDto> publicTags(String language) {
        String fallback = languageService.defaultLanguageCode();
        return tagRepository.findUsedTags().stream()
                .map(tag -> new BlogDtos.TagDto(tag.getId(), tag.getSlug(), tag.name(language, fallback)))
                .toList();
    }

    // =======================================================================
    // Admin
    // =======================================================================

    @Transactional(readOnly = true)
    public Page<BlogDtos.PostRow> adminSearch(String search, ContentStatus status, Long categoryId,
                                              String language, Pageable pageable) {
        Page<Post> page = postRepository.adminSearch(
                blankToNull(search), status, categoryId, pageable);

        Map<Long, String> covers = coverUrls(page.getContent());
        String fallback = languageService.defaultLanguageCode();
        OffsetDateTime now = OffsetDateTime.now();

        return page.map(post -> {
            var translation = post.translation(language, fallback);
            return new BlogDtos.PostRow(
                    post.getId(), post.getSlug(),
                    translation != null ? translation.getTitle() : post.getSlug(),
                    covers.get(post.getCoverMediaId()),
                    post.getCategory() == null ? null : post.getCategory().name(language, fallback),
                    post.getStatus(), post.isFeatured(), post.isScheduled(now),
                    post.getViewCount(), post.getPublishedAt(), post.getUpdatedAt());
        });
    }

    @Transactional(readOnly = true)
    public BlogDtos.PostResponse get(Long id) {
        Post post = loadPost(id);

        Map<String, BlogDtos.TranslationDto> translations = new LinkedHashMap<>();
        post.getTranslations().forEach((code, translation) -> translations.put(code,
                new BlogDtos.TranslationDto(
                        translation.getTitle(), translation.getShortDescription(),
                        translation.getContent(), translation.getSeoTitle(),
                        translation.getSeoDescription())));

        return new BlogDtos.PostResponse(
                post.getId(), post.getSlug(),
                post.getCategory() == null ? null : post.getCategory().getId(),
                post.getCoverMediaId(), mediaService.publicUrl(post.getCoverMediaId()),
                post.getStatus(), post.isFeatured(), post.getReadingMinutes(), post.getViewCount(),
                post.getPublishedAt(),
                post.getTags().stream().map(Tag::getId).toList(),
                translations);
    }

    @Transactional
    public BlogDtos.PostResponse create(BlogDtos.SavePostRequest request) {
        requireDefaultTranslation(request.translations());

        Post post = new Post();
        post.setSlug(resolveSlug(request, null));
        apply(post, request);

        Post saved = postRepository.save(post);
        log.info("Created post {} ({})", saved.getId(), saved.getSlug());
        return get(saved.getId());
    }

    @Transactional
    public BlogDtos.PostResponse update(Long id, BlogDtos.SavePostRequest request) {
        requireDefaultTranslation(request.translations());

        Post post = loadPost(id);
        post.setSlug(resolveSlug(request, post));
        apply(post, request);
        return get(id);
    }

    @Transactional
    public void changeStatus(Long id, ContentStatus status) {
        Post post = loadPost(id);
        post.setStatus(status);
        // Activating without an explicit date means "publish now".
        if (status == ContentStatus.ACTIVE && post.getPublishedAt() == null) {
            post.setPublishedAt(OffsetDateTime.now());
        }
    }

    @Transactional
    public void changeFeatured(Long id, boolean featured) {
        loadPost(id).setFeatured(featured);
    }

    @Transactional
    public void delete(Long id) {
        Post post = loadPost(id);
        post.softDelete();
        post.setStatus(ContentStatus.ARCHIVED);
    }

    // --- categories ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<BlogDtos.CategoryDto> adminCategories(String language) {
        String fallback = languageService.defaultLanguageCode();
        return categoryRepository.findAllOrdered().stream()
                .map(category -> new BlogDtos.CategoryDto(
                        category.getId(), category.getSlug(),
                        category.name(language, fallback),
                        descriptionOf(category, language, fallback),
                        category.getColor(),
                        postRepository.countInCategory(category.getId())))
                .toList();
    }

    @Transactional
    public BlogDtos.CategoryDto saveCategory(Long id, BlogDtos.SaveCategoryRequest request,
                                             String language) {
        PostCategory category = id == null
                ? new PostCategory()
                : categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Bloq kateqoriyası", id));

        String defaultLanguage = languageService.defaultLanguageCode();
        String name = request.names().get(defaultLanguage);
        if (name == null || name.isBlank()) {
            throw ApiException.field(ErrorCode.DEFAULT_LANGUAGE_REQUIRED,
                    "names." + defaultLanguage, "Əsas dil üçün ad mütləqdir");
        }

        String slug = SlugUtil.slugify(
                request.slug() != null && !request.slug().isBlank() ? request.slug() : name);
        if (categoryRepository.existsBySlug(slug, id)) {
            throw new ApiException(ErrorCode.SLUG_ALREADY_EXISTS);
        }

        category.setSlug(slug);
        category.setColor(request.color());
        category.setStatus(request.status() == null ? ContentStatus.ACTIVE : request.status());
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }

        Set<String> active = Set.copyOf(languageService.activeLanguageCodes());
        category.getTranslations().keySet().removeIf(code -> !request.names().containsKey(code));
        request.names().forEach((code, value) -> {
            if (!active.contains(code)) {
                throw new ApiException(ErrorCode.LANGUAGE_NOT_ACTIVE, "Dil aktiv deyil: " + code);
            }
            var translation = category.getTranslations()
                    .getOrDefault(code, new PostCategoryTranslation());
            translation.setName(sanitizer.stripTags(value).trim());
            category.putTranslation(code, translation);
        });

        PostCategory saved = categoryRepository.save(category);
        return new BlogDtos.CategoryDto(
                saved.getId(), saved.getSlug(), saved.name(language, defaultLanguage),
                descriptionOf(saved, language, defaultLanguage), saved.getColor(),
                postRepository.countInCategory(saved.getId()));
    }

    @Transactional
    public void deleteCategory(Long id) {
        if (postRepository.countInCategory(id) > 0) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT,
                    "Kateqoriyada yazılar var. Əvvəlcə onları başqa kateqoriyaya köçürün.");
        }
        categoryRepository.deleteById(id);
    }

    // --- tags ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<BlogDtos.TagDto> adminTags(String language) {
        String fallback = languageService.defaultLanguageCode();
        return tagRepository.findAllOrdered().stream()
                .map(tag -> new BlogDtos.TagDto(tag.getId(), tag.getSlug(), tag.name(language, fallback)))
                .toList();
    }

    @Transactional
    public BlogDtos.TagDto saveTag(BlogDtos.SaveTagRequest request, String language) {
        String code = SlugUtil.slugify(request.code()).replace('-', '_');
        Tag tag = tagRepository.findByCode(code).orElseGet(Tag::new);
        tag.setCode(code);
        tag.setSlug(SlugUtil.slugify(request.code()));

        Set<String> active = Set.copyOf(languageService.activeLanguageCodes());
        request.names().forEach((languageCode, name) -> {
            if (!active.contains(languageCode)) {
                throw new ApiException(ErrorCode.LANGUAGE_NOT_ACTIVE, "Dil aktiv deyil: " + languageCode);
            }
            var translation = tag.getTranslations().getOrDefault(languageCode, new TagTranslation());
            translation.setName(sanitizer.stripTags(name).trim());
            tag.putTranslation(languageCode, translation);
        });

        Tag saved = tagRepository.save(tag);
        return new BlogDtos.TagDto(saved.getId(), saved.getSlug(),
                saved.name(language, languageService.defaultLanguageCode()));
    }

    // =======================================================================
    // Internals
    // =======================================================================

    private void apply(Post post, BlogDtos.SavePostRequest request) {
        post.setCategory(request.categoryId() == null ? null
                : categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> ApiException.notFound("Bloq kateqoriyası", request.categoryId())));
        post.setCoverMediaId(request.coverMediaId());
        post.setFeatured(Boolean.TRUE.equals(request.featured()));

        ContentStatus status = request.status() == null ? ContentStatus.DRAFT : request.status();
        post.setStatus(status);

        if (request.publishedAt() != null) {
            post.setPublishedAt(request.publishedAt());
        } else if (status == ContentStatus.ACTIVE && post.getPublishedAt() == null) {
            post.setPublishedAt(OffsetDateTime.now());
        }

        applyTranslations(post, request.translations());

        // Estimated from the default-language body unless the editor set it explicitly.
        post.setReadingMinutes(request.readingMinutes() != null
                ? request.readingMinutes()
                : estimateReadingMinutes(post));

        applyTags(post, request.tagIds());
    }

    private void applyTranslations(Post post, Map<String, BlogDtos.TranslationDto> translations) {
        Set<String> active = Set.copyOf(languageService.activeLanguageCodes());
        post.getTranslations().keySet().removeIf(code -> !translations.containsKey(code));

        translations.forEach((code, dto) -> {
            if (!active.contains(code)) {
                throw new ApiException(ErrorCode.LANGUAGE_NOT_ACTIVE, "Dil aktiv deyil: " + code);
            }
            PostTranslation translation = post.getTranslations()
                    .getOrDefault(code, new PostTranslation());
            translation.setTitle(sanitizer.stripTags(dto.title()).trim());
            translation.setShortDescription(sanitizer.stripTags(dto.shortDescription()));
            // Only the body accepts markup, and only after sanitising.
            translation.setContent(sanitizer.sanitizeRichText(dto.content()));
            translation.setSeoTitle(sanitizer.stripTags(dto.seoTitle()));
            translation.setSeoDescription(sanitizer.stripTags(dto.seoDescription()));
            post.putTranslation(code, translation);
        });
    }

    private void applyTags(Post post, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            post.getTags().clear();
            return;
        }
        post.setTags(new LinkedHashSet<>(tagRepository.findAllByIds(tagIds)));
    }

    private Integer estimateReadingMinutes(Post post) {
        var translation = post.getTranslations().get(languageService.defaultLanguageCode());
        if (translation == null || translation.getContent() == null) {
            return null;
        }
        // Tags are stripped first so markup does not inflate the word count.
        String text = translation.getContent().replaceAll("<[^>]+>", " ");
        int words = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
        return words == 0 ? null : Math.max(1, Math.round((float) words / WORDS_PER_MINUTE));
    }

    private String resolveSlug(BlogDtos.SavePostRequest request, Post existing) {
        String requested = request.slug();
        if (requested == null || requested.isBlank()) {
            if (existing != null) {
                return existing.getSlug();
            }
            String source = request.translations()
                    .get(languageService.defaultLanguageCode()).title();
            return SlugUtil.uniqueSlug(source, postRepository::existsBySlugAndDeletedAtIsNull);
        }
        String slug = SlugUtil.slugify(requested);
        if (!SlugUtil.isValidSlug(slug)) {
            throw ApiException.field(ErrorCode.VALIDATION_ERROR, "slug", "Slug formatı yanlışdır");
        }
        if (postRepository.existsBySlug(slug, existing == null ? null : existing.getId())) {
            throw new ApiException(ErrorCode.SLUG_ALREADY_EXISTS);
        }
        return slug;
    }

    private void requireDefaultTranslation(Map<String, BlogDtos.TranslationDto> translations) {
        String defaultLanguage = languageService.defaultLanguageCode();
        var translation = translations == null ? null : translations.get(defaultLanguage);
        if (translation == null || translation.title() == null || translation.title().isBlank()) {
            throw ApiException.field(ErrorCode.DEFAULT_LANGUAGE_REQUIRED,
                    "translations." + defaultLanguage + ".title",
                    "Əsas dil (%s) üçün başlıq mütləqdir".formatted(defaultLanguage));
        }
    }

    private BlogDtos.PostCard toCard(Post post, String language, Map<Long, String> covers) {
        String fallback = languageService.defaultLanguageCode();
        var translation = post.translation(language, fallback);
        var category = post.getCategory();

        return new BlogDtos.PostCard(
                post.getId(), post.getSlug(),
                translation != null ? translation.getTitle() : post.getSlug(),
                translation != null ? translation.getShortDescription() : null,
                covers.get(post.getCoverMediaId()),
                category == null ? null : category.getId(),
                category == null ? null : category.getSlug(),
                category == null ? null : category.name(language, fallback),
                category == null ? null : category.getColor(),
                post.isFeatured(), post.getReadingMinutes(), post.getPublishedAt());
    }

    private BlogDtos.PostDetail toDetail(Post post, String language) {
        String fallback = languageService.defaultLanguageCode();
        var translation = post.translation(language, fallback);
        var category = post.getCategory();

        String authorName = post.getAuthorId() == null ? null
                : userRepository.findAliveById(post.getAuthorId())
                .map(user -> user.fullName())
                .orElse(null);

        return new BlogDtos.PostDetail(
                post.getId(), post.getSlug(),
                translation != null ? translation.getTitle() : post.getSlug(),
                translation != null ? translation.getShortDescription() : null,
                translation != null ? translation.getContent() : null,
                mediaService.publicUrl(post.getCoverMediaId()),
                translation != null && translation.getSeoTitle() != null
                        ? translation.getSeoTitle()
                        : (translation != null ? translation.getTitle() : null),
                translation != null && translation.getSeoDescription() != null
                        ? translation.getSeoDescription()
                        : (translation != null ? translation.getShortDescription() : null),
                category == null ? null : category.getSlug(),
                category == null ? null : category.name(language, fallback),
                authorName, post.getReadingMinutes(), post.getViewCount(),
                post.getTags().stream()
                        .map(tag -> new BlogDtos.TagDto(tag.getId(), tag.getSlug(),
                                tag.name(language, fallback)))
                        .toList(),
                post.getPublishedAt());
    }

    private Map<Long, String> coverUrls(List<Post> posts) {
        return mediaService.publicUrls(posts.stream()
                .map(Post::getCoverMediaId).filter(Objects::nonNull).toList());
    }

    private String descriptionOf(PostCategory category, String language, String fallback) {
        var translation = category.getTranslations().get(language);
        if (translation == null) {
            translation = category.getTranslations().get(fallback);
        }
        return translation == null ? null : translation.getDescription();
    }

    private Post loadPost(Long id) {
        return postRepository.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Bloq yazısı", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
