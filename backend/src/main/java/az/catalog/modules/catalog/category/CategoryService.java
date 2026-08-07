package az.catalog.modules.catalog.category;

import az.catalog.common.domain.ContentStatus;
import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.common.util.HtmlSanitizer;
import az.catalog.common.util.SlugUtil;
import az.catalog.modules.catalog.product.ProductRepository;
import az.catalog.modules.language.LanguageService;
import az.catalog.modules.media.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository repository;
    private final ProductRepository productRepository;
    private final LanguageService languageService;
    private final MediaService mediaService;
    private final HtmlSanitizer sanitizer;

    // -----------------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------------

    /**
     * Admin tree: counts are per-category (not cumulative), so an admin managing a
     * specific node sees exactly what is assigned to it — matches the direct-count
     * guard {@link #delete} uses to block deleting a non-empty category.
     */
    @Transactional(readOnly = true)
    public List<CategoryDtos.CategoryNode> tree(String language, boolean publishedOnly) {
        List<Category> all = publishedOnly
                ? repository.findAllByStatus(ContentStatus.ACTIVE)
                : repository.findAllAlive();
        return buildTree(all, language, countsByCategory(all));
    }

    /**
     * Storefront tree: counts include descendants, since picking a parent category
     * (e.g. "Mebel") filters products across its whole subtree — the displayed count
     * must match what actually shows up after the click.
     */
    @Transactional(readOnly = true)
    public List<CategoryDtos.CategoryNode> publicTree(String language) {
        List<Category> all = repository.findAllByStatus(ContentStatus.ACTIVE);
        return buildTree(all, language, cumulativeCounts(all, countsByCategory(all)));
    }

    @Transactional(readOnly = true)
    public List<CategoryDtos.CategoryCard> publishedList(String language) {
        List<Category> all = repository.findAllByStatus(ContentStatus.ACTIVE);
        Map<Long, Long> counts = cumulativeCounts(all, countsByCategory(all));
        String fallback = languageService.defaultLanguageCode();
        return all.stream().map(category -> toCard(category, language, fallback, counts)).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryDtos.CategoryCard> featured(String language, int limit) {
        List<Category> categories = repository.findFeatured(PageRequest.of(0, limit));
        List<Category> all = repository.findAllByStatus(ContentStatus.ACTIVE);
        Map<Long, Long> counts = cumulativeCounts(all, countsByCategory(all));
        String fallback = languageService.defaultLanguageCode();
        return categories.stream().map(category -> toCard(category, language, fallback, counts)).toList();
    }

    @Transactional(readOnly = true)
    public CategoryDtos.CategoryCard publishedBySlug(String slug, String language) {
        Category category = repository.findPublishedBySlug(slug)
                .orElseThrow(() -> ApiException.notFoundBySlug("Kateqoriya", slug));
        long count = productRepository.countPublishedInCategories(repository.findSubtreeIds(category.getId()));
        return toCard(category, language, languageService.defaultLanguageCode(),
                Map.of(category.getId(), count));
    }

    /** Root → … → category, for the storefront breadcrumb. */
    @Transactional(readOnly = true)
    public List<CategoryDtos.CategoryCard> breadcrumb(String slug, String language) {
        Category category = repository.findPublishedBySlug(slug)
                .orElseThrow(() -> ApiException.notFoundBySlug("Kateqoriya", slug));

        List<Long> ancestorIds = parseAncestorIds(category.getPath());
        if (ancestorIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Category> byId = repository.findAllAliveByIds(ancestorIds).stream()
                .collect(Collectors.toMap(Category::getId, c -> c));
        String fallback = languageService.defaultLanguageCode();

        return ancestorIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(c -> toCard(c, language, fallback, Map.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryDtos.CategoryResponse get(Long id) {
        Category category = load(id);
        Map<String, CategoryDtos.TranslationDto> translations = new LinkedHashMap<>();
        category.getTranslations().forEach((code, translation) -> translations.put(code,
                new CategoryDtos.TranslationDto(translation.getName(), translation.getDescription(),
                        translation.getSeoTitle(), translation.getSeoDescription())));

        return new CategoryDtos.CategoryResponse(
                category.getId(),
                category.getParent() == null ? null : category.getParent().getId(),
                category.getSlug(), category.getImageMediaId(),
                mediaService.publicUrl(category.getImageMediaId()),
                category.getIconMediaId(), category.getBannerMediaId(), category.getIcon(),
                category.getStatus(), category.isFeatured(), category.isShowInMenu(),
                category.getPath(), category.getDepth(), category.getSortOrder(),
                productRepository.countActiveInCategory(category.getId()),
                translations);
    }

    /** Category plus all descendants — the id set a product filter should match on. */
    @Transactional(readOnly = true)
    public List<Long> subtreeIds(Long categoryId) {
        return repository.findSubtreeIds(categoryId);
    }

    // -----------------------------------------------------------------------
    // Writes
    // -----------------------------------------------------------------------

    @Transactional
    public CategoryDtos.CategoryResponse create(CategoryDtos.SaveRequest request) {
        requireDefaultTranslation(request.translations());

        Category category = new Category();
        category.setSlug(resolveSlug(request, null));
        applyScalars(category, request);
        applyParent(category, request.parentId());
        applyTranslations(category, request.translations());

        Category saved = repository.save(category);
        repository.flush();
        // Path needs the generated id, so it is computed after the first flush.
        recomputePath(saved);
        return get(saved.getId());
    }

    @Transactional
    public CategoryDtos.CategoryResponse update(Long id, CategoryDtos.SaveRequest request) {
        requireDefaultTranslation(request.translations());
        Category category = load(id);

        Long newParentId = request.parentId();
        boolean parentChanged = !Objects.equals(
                category.getParent() == null ? null : category.getParent().getId(), newParentId);

        if (parentChanged) {
            guardAgainstCycle(category, newParentId);
        }

        category.setSlug(resolveSlug(request, category));
        applyScalars(category, request);
        applyParent(category, newParentId);
        applyTranslations(category, request.translations());

        if (parentChanged) {
            repository.flush();
            recomputeSubtreePaths(category);
        }
        return get(id);
    }

    @Transactional
    public void changeStatus(Long id, ContentStatus status) {
        Category category = load(id);
        category.setStatus(status);
        if (status != ContentStatus.ACTIVE) {
            // Hiding a parent must hide its children, otherwise the storefront would
            // surface an orphaned subtree with no way to navigate to it.
            repository.findSubtree(id, category.getPath())
                    .forEach(descendant -> descendant.setStatus(status));
        }
    }

    @Transactional
    public void reorder(CategoryDtos.ReorderRequest request) {
        Map<Long, Category> byId = repository
                .findAllAliveByIds(request.items().stream().map(CategoryDtos.ReorderItem::id).toList())
                .stream().collect(Collectors.toMap(Category::getId, c -> c));

        for (CategoryDtos.ReorderItem item : request.items()) {
            Category category = byId.get(item.id());
            if (category == null) {
                continue;
            }
            category.setSortOrder(item.sortOrder());
            Long currentParentId = category.getParent() == null ? null : category.getParent().getId();
            if (!Objects.equals(currentParentId, item.parentId())) {
                guardAgainstCycle(category, item.parentId());
                applyParent(category, item.parentId());
            }
        }
        repository.flush();
        // Reparenting during a drag-and-drop reorder invalidates paths.
        byId.values().forEach(this::recomputeSubtreePaths);
    }

    @Transactional
    public void delete(Long id) {
        Category category = load(id);

        if (repository.countChildren(id) > 0) {
            throw new ApiException(ErrorCode.CATEGORY_HAS_CHILDREN);
        }
        if (productRepository.countActiveInCategory(id) > 0) {
            throw new ApiException(ErrorCode.CATEGORY_HAS_ACTIVE_PRODUCTS);
        }
        category.softDelete();
        log.info("Soft deleted category {} ({})", id, category.getSlug());
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private void applyScalars(Category category, CategoryDtos.SaveRequest request) {
        category.setImageMediaId(request.imageMediaId());
        category.setIconMediaId(request.iconMediaId());
        category.setBannerMediaId(request.bannerMediaId());
        category.setIcon(request.icon());
        category.setStatus(request.status() == null ? ContentStatus.ACTIVE : request.status());
        category.setFeatured(Boolean.TRUE.equals(request.featured()));
        category.setShowInMenu(request.showInMenu() == null || request.showInMenu());
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
    }

    private void applyParent(Category category, Long parentId) {
        if (parentId == null) {
            category.setParent(null);
            category.setDepth(0);
            return;
        }
        Category parent = load(parentId);
        category.setParent(parent);
        category.setDepth(parent.getDepth() + 1);
    }

    private void applyTranslations(Category category, Map<String, CategoryDtos.TranslationDto> translations) {
        Set<String> active = Set.copyOf(languageService.activeLanguageCodes());
        category.getTranslations().keySet().removeIf(code -> !translations.containsKey(code));

        translations.forEach((code, dto) -> {
            if (!active.contains(code)) {
                throw new ApiException(ErrorCode.LANGUAGE_NOT_ACTIVE, "Dil aktiv deyil: " + code);
            }
            CategoryTranslation translation = category.getTranslations()
                    .getOrDefault(code, new CategoryTranslation());
            translation.setName(sanitizer.stripTags(dto.name()).trim());
            translation.setDescription(sanitizer.sanitizeRichText(dto.description()));
            translation.setSeoTitle(sanitizer.stripTags(dto.seoTitle()));
            translation.setSeoDescription(sanitizer.stripTags(dto.seoDescription()));
            category.putTranslation(code, translation);
        });
    }

    private void requireDefaultTranslation(Map<String, CategoryDtos.TranslationDto> translations) {
        String defaultLanguage = languageService.defaultLanguageCode();
        var translation = translations == null ? null : translations.get(defaultLanguage);
        if (translation == null || translation.name() == null || translation.name().isBlank()) {
            throw ApiException.field(ErrorCode.DEFAULT_LANGUAGE_REQUIRED,
                    "translations." + defaultLanguage + ".name",
                    "Əsas dil (%s) üçün ad mütləqdir".formatted(defaultLanguage));
        }
    }

    private String resolveSlug(CategoryDtos.SaveRequest request, Category existing) {
        String requested = request.slug();
        if (requested == null || requested.isBlank()) {
            if (existing != null) {
                return existing.getSlug();
            }
            String source = request.translations().get(languageService.defaultLanguageCode()).name();
            return SlugUtil.uniqueSlug(source, repository::existsBySlugAndDeletedAtIsNull);
        }
        String slug = SlugUtil.slugify(requested);
        if (!SlugUtil.isValidSlug(slug)) {
            throw ApiException.field(ErrorCode.VALIDATION_ERROR, "slug", "Slug formatı yanlışdır");
        }
        if (repository.existsBySlug(slug, existing == null ? null : existing.getId())) {
            throw new ApiException(ErrorCode.SLUG_ALREADY_EXISTS);
        }
        return slug;
    }

    /** Prevents making a category a descendant of itself. */
    private void guardAgainstCycle(Category category, Long newParentId) {
        if (newParentId == null) {
            return;
        }
        if (newParentId.equals(category.getId())) {
            throw new ApiException(ErrorCode.CATEGORY_CYCLE);
        }
        if (category.getId() != null && repository.findSubtreeIds(category.getId()).contains(newParentId)) {
            throw new ApiException(ErrorCode.CATEGORY_CYCLE);
        }
    }

    private void recomputePath(Category category) {
        String parentPath = category.getParent() == null ? "" : category.getParent().getPath();
        category.setPath(parentPath.isEmpty()
                ? String.valueOf(category.getId())
                : parentPath + "/" + category.getId());
        category.setDepth(category.getParent() == null ? 0 : category.getParent().getDepth() + 1);
    }

    /** Recomputes the path for a category and everything beneath it, breadth-first. */
    private void recomputeSubtreePaths(Category root) {
        recomputePath(root);
        List<Category> queue = new ArrayList<>(repository.findChildren(root.getId()));
        while (!queue.isEmpty()) {
            Category current = queue.remove(0);
            recomputePath(current);
            queue.addAll(repository.findChildren(current.getId()));
        }
    }

    private Map<Long, Long> countsByCategory(List<Category> categories) {
        Map<Long, Long> counts = new HashMap<>();
        for (Category category : categories) {
            counts.put(category.getId(), productRepository.countActiveInCategory(category.getId()));
        }
        return counts;
    }

    /** Rolls direct per-category counts up into each category's own count plus all descendants'. */
    private Map<Long, Long> cumulativeCounts(List<Category> all, Map<Long, Long> direct) {
        Map<Long, List<Category>> byParent = all.stream()
                .collect(Collectors.groupingBy(c -> c.getParent() == null ? 0L : c.getParent().getId()));
        Map<Long, Long> cumulative = new HashMap<>();
        for (Category category : all) {
            cumulative.put(category.getId(), subtreeSum(category, byParent, direct));
        }
        return cumulative;
    }

    private long subtreeSum(Category category, Map<Long, List<Category>> byParent, Map<Long, Long> direct) {
        long total = direct.getOrDefault(category.getId(), 0L);
        for (Category child : byParent.getOrDefault(category.getId(), List.of())) {
            total += subtreeSum(child, byParent, direct);
        }
        return total;
    }

    private CategoryDtos.CategoryCard toCard(Category category, String language, String fallback,
                                             Map<Long, Long> counts) {
        CategoryTranslation translation = category.translation(language, fallback);
        return new CategoryDtos.CategoryCard(
                category.getId(), category.getSlug(),
                translation != null ? translation.getName() : category.getSlug(),
                translation != null ? translation.getDescription() : null,
                category.getIcon(),
                mediaService.publicUrl(category.getImageMediaId()),
                counts.getOrDefault(category.getId(), 0L),
                category.isFeatured());
    }

    private List<CategoryDtos.CategoryNode> buildTree(List<Category> all, String language,
                                                      Map<Long, Long> counts) {
        String fallback = languageService.defaultLanguageCode();
        Map<Long, List<Category>> byParent = all.stream()
                .collect(Collectors.groupingBy(
                        category -> category.getParent() == null ? 0L : category.getParent().getId()));
        return buildNodes(byParent, 0L, language, fallback, counts);
    }

    private List<CategoryDtos.CategoryNode> buildNodes(Map<Long, List<Category>> byParent, Long parentId,
                                                       String language, String fallback,
                                                       Map<Long, Long> counts) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .sorted(Comparator.comparingInt(Category::getSortOrder).thenComparing(Category::getId))
                .map(category -> {
                    CategoryTranslation translation = category.translation(language, fallback);
                    return new CategoryDtos.CategoryNode(
                            category.getId(),
                            category.getParent() == null ? null : category.getParent().getId(),
                            category.getSlug(),
                            translation != null ? translation.getName() : category.getSlug(),
                            category.getIcon(),
                            mediaService.publicUrl(category.getImageMediaId()),
                            category.getStatus(), category.isFeatured(), category.isShowInMenu(),
                            category.getDepth(), category.getSortOrder(),
                            counts.getOrDefault(category.getId(), 0L),
                            buildNodes(byParent, category.getId(), language, fallback, counts));
                })
                .toList();
    }

    private List<Long> parseAncestorIds(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (!segment.isBlank()) {
                ids.add(Long.valueOf(segment));
            }
        }
        return ids;
    }

    Category load(Long id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Kateqoriya", id));
    }
}
