package az.catalog.modules.navigation;

import az.catalog.common.config.CacheConfig;
import az.catalog.common.domain.VersionStatus;
import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.common.util.HtmlSanitizer;
import az.catalog.modules.language.LanguageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Navigation menus.
 *
 * Same draft/publish contract as pages: items are edited on a DRAFT version and the
 * header only changes when the operator publishes. The nesting is rebuilt in memory from
 * a flat item list — walking a self-referencing association through JPA costs one query
 * per level, and this runs on every storefront request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

    /** Sub-menus render one level deep; anything further would not be reachable on mobile. */
    private static final int MAX_DEPTH = 2;

    private final MenuRepository menuRepository;
    private final MenuVersionRepository versionRepository;
    private final LanguageService languageService;
    private final HtmlSanitizer sanitizer;

    // =======================================================================
    // Menus
    // =======================================================================

    @Transactional(readOnly = true)
    public List<MenuDtos.MenuRow> list() {
        return menuRepository.findAllOrdered().stream()
                .map(menu -> {
                    var published = versionRepository.findByMenuAndStatus(
                            menu.getId(), VersionStatus.PUBLISHED);
                    var draft = versionRepository.findByMenuAndStatus(
                            menu.getId(), VersionStatus.DRAFT);

                    return new MenuDtos.MenuRow(
                            menu.getId(), menu.getCode(), menu.getName(), menu.getLocation(),
                            menu.getLocation().label(), menu.isActive(),
                            published.map(version -> version.getItems().size()).orElse(0),
                            draft.isPresent(),
                            published.map(MenuVersion::getPublishedAt).orElse(null));
                })
                .toList();
    }

    @Transactional
    public MenuDtos.MenuRow create(MenuDtos.SaveMenuRequest request) {
        String code = request.code().trim().toLowerCase();
        if (menuRepository.existsByCode(code, null)) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "Bu kod artıq mövcuddur: " + code);
        }

        Menu menu = menuRepository.save(Menu.builder()
                .code(code)
                .name(request.name().trim())
                .location(request.location())
                .active(request.active() == null || request.active())
                .build());

        // A menu with no version cannot be edited, so an empty draft is opened with it.
        versionRepository.save(MenuVersion.builder()
                .menu(menu)
                .versionNumber(1)
                .status(VersionStatus.DRAFT)
                .build());

        log.info("Created menu {} at {}", code, request.location());
        return list().stream()
                .filter(row -> row.id().equals(menu.getId()))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    @CacheEvict(value = CacheConfig.NAVIGATION, allEntries = true)
    public MenuDtos.MenuRow update(Long id, MenuDtos.SaveMenuRequest request) {
        Menu menu = loadMenu(id);
        String code = request.code().trim().toLowerCase();
        if (menuRepository.existsByCode(code, id)) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "Bu kod artıq mövcuddur");
        }
        menu.setCode(code);
        menu.setName(request.name().trim());
        menu.setLocation(request.location());
        if (request.active() != null) {
            menu.setActive(request.active());
        }
        return list().stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    @CacheEvict(value = CacheConfig.NAVIGATION, allEntries = true)
    public void delete(Long id) {
        menuRepository.delete(loadMenu(id));
    }

    // =======================================================================
    // Versions
    // =======================================================================

    /** Draft if one is open, otherwise the published version. */
    @Transactional(readOnly = true)
    public MenuDtos.VersionResponse editableVersion(Long menuId, String language) {
        return versionRepository.findByMenuAndStatus(menuId, VersionStatus.DRAFT)
                .or(() -> versionRepository.findByMenuAndStatus(menuId, VersionStatus.PUBLISHED))
                .map(version -> toVersionResponse(version, language))
                .orElseThrow(() -> ApiException.notFound("Menyu versiyası", menuId));
    }

    @Transactional
    public MenuDtos.VersionResponse createDraft(Long menuId, String language) {
        Menu menu = loadMenu(menuId);

        var existing = versionRepository.findByMenuAndStatus(menuId, VersionStatus.DRAFT);
        if (existing.isPresent()) {
            return toVersionResponse(existing.get(), language);
        }

        MenuVersion draft = MenuVersion.builder()
                .menu(menu)
                .versionNumber(versionRepository.maxVersionNumber(menuId) + 1)
                .status(VersionStatus.DRAFT)
                .build();

        // Clone the live tree so editing starts from what visitors currently see.
        versionRepository.findByMenuAndStatus(menuId, VersionStatus.PUBLISHED)
                .ifPresent(published -> cloneItems(published, draft));

        MenuVersion saved = versionRepository.save(draft);
        log.info("Opened draft v{} for menu {}", saved.getVersionNumber(), menu.getCode());
        return toVersionResponse(saved, language);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.NAVIGATION, allEntries = true)
    public MenuDtos.VersionResponse publish(Long menuId, String language) {
        MenuVersion draft = versionRepository.findByMenuAndStatus(menuId, VersionStatus.DRAFT)
                .orElseThrow(() -> new ApiException(ErrorCode.NO_DRAFT_TO_PUBLISH));

        versionRepository.findByMenuAndStatus(menuId, VersionStatus.PUBLISHED)
                .ifPresent(current -> current.setStatus(VersionStatus.ARCHIVED));
        versionRepository.flush();

        draft.setStatus(VersionStatus.PUBLISHED);
        draft.setPublishedAt(OffsetDateTime.now());

        log.info("Published menu {} v{}", draft.getMenu().getCode(), draft.getVersionNumber());
        return toVersionResponse(draft, language);
    }

    @Transactional
    public void discardDraft(Long menuId) {
        versionRepository.findByMenuAndStatus(menuId, VersionStatus.DRAFT)
                .ifPresent(versionRepository::delete);
    }

    // =======================================================================
    // Items
    // =======================================================================

    @Transactional
    public MenuDtos.VersionResponse addItem(Long versionId, MenuDtos.SaveItemRequest request,
                                            String language) {
        MenuVersion version = loadVersion(versionId);
        requireEditable(version);

        MenuItem item = new MenuItem();
        applyItem(version, item, request);
        item.setSortOrder(request.sortOrder() == null
                ? countSiblings(version, request.parentId()) : request.sortOrder());

        version.addItem(item);
        return toVersionResponse(version, language);
    }

    @Transactional
    public MenuDtos.VersionResponse updateItem(Long versionId, Long itemId,
                                               MenuDtos.SaveItemRequest request, String language) {
        MenuVersion version = loadVersion(versionId);
        requireEditable(version);

        MenuItem item = findItem(version, itemId);
        applyItem(version, item, request);
        return toVersionResponse(version, language);
    }

    /** Removing a parent removes its children — an orphaned sub-item is unreachable. */
    @Transactional
    public MenuDtos.VersionResponse removeItem(Long versionId, Long itemId, String language) {
        MenuVersion version = loadVersion(versionId);
        requireEditable(version);

        Set<Long> toRemove = collectSubtreeIds(version, itemId);
        version.getItems().removeIf(item -> toRemove.contains(item.getId()));

        return toVersionResponse(version, language);
    }

    /**
     * Applies a whole drag-and-drop result at once. Reparenting is validated against
     * cycles and depth before anything is written.
     */
    @Transactional
    public MenuDtos.VersionResponse reorder(Long versionId, MenuDtos.ReorderRequest request,
                                            String language) {
        MenuVersion version = loadVersion(versionId);
        requireEditable(version);

        Map<Long, MenuItem> byId = version.getItems().stream()
                .collect(Collectors.toMap(MenuItem::getId, item -> item));

        // Validate the complete proposed layout first; a partially applied move would
        // leave the menu in a shape the operator never asked for.
        Map<Long, Long> proposedParents = new HashMap<>();
        version.getItems().forEach(item ->
                proposedParents.put(item.getId(),
                        item.getParent() == null ? null : item.getParent().getId()));
        request.items().forEach(entry -> proposedParents.put(entry.id(), entry.parentId()));

        for (var entry : request.items()) {
            if (!byId.containsKey(entry.id())) {
                throw ApiException.notFound("Menyu elementi", entry.id());
            }
            validateHierarchy(entry.id(), entry.parentId(), proposedParents);
        }

        for (var entry : request.items()) {
            MenuItem item = byId.get(entry.id());
            item.setSortOrder(entry.sortOrder());
            item.setParent(entry.parentId() == null ? null : byId.get(entry.parentId()));
        }

        return toVersionResponse(version, language);
    }

    // =======================================================================
    // Public
    // =======================================================================

    /** Menu for a storefront location. Hidden items are filtered before caching. */
    @Cacheable(value = CacheConfig.NAVIGATION, key = "#location.name() + ':' + #language")
    @Transactional(readOnly = true)
    public List<MenuDtos.PublicMenuItem> publicMenu(Menu.Location location, String language) {
        return versionRepository.findPublishedByLocation(location)
                .map(version -> buildPublicTree(version, language))
                .orElseGet(List::of);
    }

    // =======================================================================
    // Internals
    // =======================================================================

    private void applyItem(MenuVersion version, MenuItem item, MenuDtos.SaveItemRequest request) {
        if (request.parentId() != null) {
            MenuItem parent = findItem(version, request.parentId());
            if (item.getId() != null && request.parentId().equals(item.getId())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Element özünün alt elementi ola bilməz");
            }
            if (parent.getParent() != null) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Menyu maksimum %d səviyyə ola bilər".formatted(MAX_DEPTH));
            }
            item.setParent(parent);
        } else {
            item.setParent(null);
        }

        item.setTitle(sanitizer.stripTags(request.title()).trim());
        item.setUrl(safeUrl(request.url()));
        item.setLinkType(request.linkType() == null ? MenuItem.LinkType.CUSTOM : request.linkType());
        item.setReferenceId(request.referenceId());
        item.setIcon(request.icon());
        item.setDescription(sanitizer.stripTags(request.description()));
        item.setTarget("_blank".equals(request.target()) ? "_blank" : "_self");
        item.setHighlight(Boolean.TRUE.equals(request.highlight()));
        if (request.active() != null) {
            item.setActive(request.active());
        }

        applyTranslations(item, request.translations());
    }

    private void applyTranslations(MenuItem item, Map<String, MenuDtos.TranslationDto> translations) {
        if (translations == null) {
            return;
        }
        Set<String> active = Set.copyOf(languageService.activeLanguageCodes());
        item.getTranslations().keySet().removeIf(code -> !translations.containsKey(code));

        translations.forEach((code, dto) -> {
            if (!active.contains(code)) {
                throw new ApiException(ErrorCode.LANGUAGE_NOT_ACTIVE, "Dil aktiv deyil: " + code);
            }
            MenuItemTranslation translation = item.getTranslations()
                    .getOrDefault(code, new MenuItemTranslation());
            translation.setTitle(sanitizer.stripTags(dto.title()).trim());
            translation.setDescription(sanitizer.stripTags(dto.description()));
            item.putTranslation(code, translation);
        });
    }

    /** Rejects a move that would nest an item inside its own subtree, or exceed the depth. */
    private void validateHierarchy(Long itemId, Long parentId, Map<Long, Long> parents) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(itemId)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Element özünün valideyni ola bilməz");
        }

        int depth = 1;
        Long cursor = parentId;
        Set<Long> visited = new java.util.HashSet<>();
        while (cursor != null) {
            if (cursor.equals(itemId) || !visited.add(cursor)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Menyuda dövrə yaranır — element öz alt elementinə köçürülə bilməz");
            }
            depth++;
            if (depth > MAX_DEPTH) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Menyu maksimum %d səviyyə ola bilər".formatted(MAX_DEPTH));
            }
            cursor = parents.get(cursor);
        }
    }

    private Set<Long> collectSubtreeIds(MenuVersion version, Long rootId) {
        Set<Long> ids = new java.util.HashSet<>();
        ids.add(rootId);
        version.getItems().stream()
                .filter(item -> item.getParent() != null && item.getParent().getId().equals(rootId))
                .forEach(child -> ids.add(child.getId()));
        return ids;
    }

    private int countSiblings(MenuVersion version, Long parentId) {
        return (int) version.getItems().stream()
                .filter(item -> Objects.equals(
                        item.getParent() == null ? null : item.getParent().getId(), parentId))
                .count();
    }

    private void cloneItems(MenuVersion source, MenuVersion target) {
        Map<Long, MenuItem> copies = new LinkedHashMap<>();

        // Roots first so a child always finds its cloned parent already present.
        source.getItems().stream()
                .filter(item -> item.getParent() == null)
                .sorted(Comparator.comparingInt(MenuItem::getSortOrder))
                .forEach(root -> {
                    MenuItem copy = root.copyShallow();
                    copies.put(root.getId(), copy);
                    target.addItem(copy);
                });

        source.getItems().stream()
                .filter(item -> item.getParent() != null)
                .sorted(Comparator.comparingInt(MenuItem::getSortOrder))
                .forEach(child -> {
                    MenuItem copy = child.copyShallow();
                    copy.setParent(copies.get(child.getParent().getId()));
                    target.addItem(copy);
                });
    }

    private MenuDtos.VersionResponse toVersionResponse(MenuVersion version, String language) {
        String fallback = languageService.defaultLanguageCode();

        Map<Long, List<MenuItem>> byParent = version.getItems().stream()
                .collect(Collectors.groupingBy(
                        item -> item.getParent() == null ? 0L : item.getParent().getId()));

        return new MenuDtos.VersionResponse(
                version.getId(), version.getMenu().getId(), version.getMenu().getCode(),
                version.getMenu().getName(), version.getMenu().getLocation(),
                version.getVersionNumber(), version.getStatus(), version.getPublishedAt(),
                buildAdminTree(byParent, 0L, language, fallback));
    }

    private List<MenuDtos.ItemResponse> buildAdminTree(Map<Long, List<MenuItem>> byParent,
                                                       Long parentId, String language,
                                                       String fallback) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .sorted(Comparator.comparingInt(MenuItem::getSortOrder).thenComparing(MenuItem::getId))
                .map(item -> {
                    Map<String, MenuDtos.TranslationDto> translations = new LinkedHashMap<>();
                    item.getTranslations().forEach((code, translation) -> translations.put(code,
                            new MenuDtos.TranslationDto(translation.getTitle(),
                                    translation.getDescription())));

                    return new MenuDtos.ItemResponse(
                            item.getId(),
                            item.getParent() == null ? null : item.getParent().getId(),
                            item.title(language, fallback), item.getUrl(), item.getLinkType(),
                            item.getReferenceId(), item.getIcon(),
                            item.description(language, fallback), item.getTarget(),
                            item.isHighlight(), item.getSortOrder(), item.isActive(),
                            translations,
                            buildAdminTree(byParent, item.getId(), language, fallback));
                })
                .toList();
    }

    private List<MenuDtos.PublicMenuItem> buildPublicTree(MenuVersion version, String language) {
        String fallback = languageService.defaultLanguageCode();

        // Hidden items are dropped here, along with their children.
        List<MenuItem> visible = version.getItems().stream()
                .filter(MenuItem::isActive)
                .toList();
        Set<Long> visibleIds = visible.stream().map(MenuItem::getId).collect(Collectors.toSet());

        Map<Long, List<MenuItem>> byParent = visible.stream()
                .filter(item -> item.getParent() == null || visibleIds.contains(item.getParent().getId()))
                .collect(Collectors.groupingBy(
                        item -> item.getParent() == null ? 0L : item.getParent().getId()));

        return buildPublicNodes(byParent, 0L, language, fallback);
    }

    private List<MenuDtos.PublicMenuItem> buildPublicNodes(Map<Long, List<MenuItem>> byParent,
                                                           Long parentId, String language,
                                                           String fallback) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .sorted(Comparator.comparingInt(MenuItem::getSortOrder).thenComparing(MenuItem::getId))
                .map(item -> new MenuDtos.PublicMenuItem(
                        item.getId(), item.title(language, fallback), item.getUrl(),
                        item.getLinkType().name(), item.getIcon(),
                        item.description(language, fallback), item.getTarget(), item.isHighlight(),
                        buildPublicNodes(byParent, item.getId(), language, fallback)))
                .toList();
    }

    private String safeUrl(String url) {
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase();
        boolean allowed = trimmed.startsWith("/")
                || trimmed.startsWith("#")
                || lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("mailto:")
                || lower.startsWith("tel:");
        if (!allowed) {
            throw ApiException.field(ErrorCode.VALIDATION_ERROR, "url",
                    "Link nisbi yol (/…) və ya http, https, mailto, tel olmalıdır");
        }
        return trimmed;
    }

    private void requireEditable(MenuVersion version) {
        if (version.getStatus() != VersionStatus.DRAFT) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE,
                    "Yalnız draft versiya redaktə edilə bilər. Əvvəlcə yeni draft yaradın.");
        }
    }

    private MenuItem findItem(MenuVersion version, Long itemId) {
        return version.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Menyu elementi", itemId));
    }

    private Menu loadMenu(Long id) {
        return menuRepository.findById(id).orElseThrow(() -> ApiException.notFound("Menyu", id));
    }

    private MenuVersion loadVersion(Long id) {
        return versionRepository.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Menyu versiyası", id));
    }
}
