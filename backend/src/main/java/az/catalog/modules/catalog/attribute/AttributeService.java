package az.catalog.modules.catalog.attribute;

import az.catalog.common.domain.ContentStatus;
import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.common.util.HtmlSanitizer;
import az.catalog.modules.language.LanguageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttributeService {

    private final AttributeRepository repository;
    private final AttributeOptionRepository optionRepository;
    private final AttributeGroupRepository groupRepository;
    private final LanguageService languageService;
    private final HtmlSanitizer sanitizer;

    // =======================================================================
    // Reads
    // =======================================================================

    /**
     * All attributes, resolved to one language. This is what the product editor loads to
     * decide which specification inputs to render.
     */
    @Transactional(readOnly = true)
    public List<AttributeDtos.AttributeResponse> list(ContentStatus status, String language) {
        List<Long> ids = repository.findAllOrdered(status).stream().map(Attribute::getId).toList();
        if (ids.isEmpty()) {
            // An empty IN () is not valid SQL on every dialect, so the query is skipped.
            return List.of();
        }
        String fallback = languageService.defaultLanguageCode();
        // Options are needed for SELECT inputs, so the list is fetched with them attached.
        return repository.findAllDetailByIds(ids).stream()
                .sorted(java.util.Comparator.comparingInt(Attribute::getSortOrder))
                .map(attribute -> toResponse(attribute, language, fallback, false))
                .toList();
    }

    /** Only the facets the storefront filter panel should show. */
    @Transactional(readOnly = true)
    public List<AttributeDtos.AttributeResponse> filterable(String language) {
        String fallback = languageService.defaultLanguageCode();
        return repository.findFilterable().stream()
                .map(attribute -> toResponse(attribute, language, fallback, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public AttributeDtos.AttributeResponse get(Long id, String language) {
        Attribute attribute = load(id);
        return toResponse(attribute, language, languageService.defaultLanguageCode(), true);
    }

    @Transactional(readOnly = true)
    public List<AttributeDtos.GroupResponse> groups(String language) {
        String fallback = languageService.defaultLanguageCode();
        return groupRepository.findAllOrdered().stream()
                .map(group -> new AttributeDtos.GroupResponse(
                        group.getId(), group.getCode(), group.name(language, fallback),
                        group.getSortOrder()))
                .toList();
    }

    // =======================================================================
    // Attribute writes
    // =======================================================================

    @Transactional
    public AttributeDtos.AttributeResponse create(AttributeDtos.SaveRequest request, String language) {
        if (repository.existsByCode(request.code())) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "Bu kod artıq mövcuddur: " + request.code());
        }
        Attribute attribute = new Attribute();
        attribute.setCode(request.code());
        attribute.setDataType(request.dataType());
        applyScalars(attribute, request);
        applyTranslations(attribute, request.translations());

        Attribute saved = repository.save(attribute);
        log.info("Created attribute {} ({})", saved.getCode(), saved.getDataType());
        return get(saved.getId(), language);
    }

    @Transactional
    public AttributeDtos.AttributeResponse update(Long id, AttributeDtos.SaveRequest request,
                                                  String language) {
        Attribute attribute = load(id);

        // Changing the data type would orphan every value already stored in the old
        // typed column, so it is refused once the attribute is in use.
        if (attribute.getDataType() != request.dataType() && repository.countUsages(id) > 0) {
            throw new ApiException(ErrorCode.ATTRIBUTE_IN_USE,
                    "Attribut məhsullarda istifadə olunduğu üçün tipi dəyişdirilə bilməz");
        }
        if (!attribute.getCode().equals(request.code()) && repository.existsByCode(request.code())) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "Bu kod artıq mövcuddur");
        }

        attribute.setCode(request.code());
        attribute.setDataType(request.dataType());
        applyScalars(attribute, request);
        applyTranslations(attribute, request.translations());

        return get(id, language);
    }

    @Transactional
    public void changeStatus(Long id, ContentStatus status) {
        load(id).setStatus(status);
    }

    @Transactional
    public void delete(Long id) {
        Attribute attribute = load(id);
        if (repository.countUsages(id) > 0) {
            throw new ApiException(ErrorCode.ATTRIBUTE_IN_USE);
        }
        repository.delete(attribute);
    }

    // =======================================================================
    // Options
    // =======================================================================

    @Transactional
    public AttributeDtos.AttributeResponse addOption(Long attributeId, AttributeDtos.OptionRequest request,
                                                     String language) {
        Attribute attribute = load(attributeId);
        if (!attribute.usesOptions()) {
            throw ApiException.badRequest("Yalnız SELECT və MULTI_SELECT attributlarına seçim əlavə edilə bilər");
        }
        boolean duplicate = attribute.getOptions().stream()
                .anyMatch(option -> option.getValue().equalsIgnoreCase(request.value()));
        if (duplicate) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "Bu dəyər artıq mövcuddur");
        }

        AttributeOption option = AttributeOption.builder()
                .attribute(attribute)
                .value(request.value().trim())
                .colorHex(request.colorHex())
                .sortOrder(request.sortOrder() == null ? attribute.getOptions().size() : request.sortOrder())
                .build();
        applyOptionLabels(option, request.labels());

        attribute.getOptions().add(option);
        return get(attributeId, language);
    }

    @Transactional
    public AttributeDtos.AttributeResponse updateOption(Long attributeId, Long optionId,
                                                        AttributeDtos.OptionRequest request,
                                                        String language) {
        Attribute attribute = load(attributeId);
        AttributeOption option = attribute.getOptions().stream()
                .filter(candidate -> candidate.getId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Seçim", optionId));

        option.setValue(request.value().trim());
        option.setColorHex(request.colorHex());
        if (request.sortOrder() != null) {
            option.setSortOrder(request.sortOrder());
        }
        applyOptionLabels(option, request.labels());

        return get(attributeId, language);
    }

    /**
     * An option in use is never physically removed — deleting it would silently blank the
     * specification on every product that selected it.
     */
    @Transactional
    public AttributeDtos.AttributeResponse deleteOption(Long attributeId, Long optionId, String language) {
        Attribute attribute = load(attributeId);
        if (optionRepository.countUsages(optionId) > 0) {
            throw new ApiException(ErrorCode.ATTRIBUTE_IN_USE,
                    "Bu seçim məhsullarda istifadə olunur və silinə bilməz");
        }
        attribute.getOptions().removeIf(option -> option.getId().equals(optionId));
        return get(attributeId, language);
    }

    // =======================================================================
    // Groups
    // =======================================================================

    @Transactional
    public AttributeDtos.GroupResponse createGroup(AttributeDtos.GroupRequest request, String language) {
        if (groupRepository.existsByCode(request.code())) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "Bu qrup kodu artıq mövcuddur");
        }
        AttributeGroup group = AttributeGroup.builder()
                .code(request.code())
                .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
                .build();

        Set<String> active = Set.copyOf(languageService.activeLanguageCodes());
        request.names().forEach((code, name) -> {
            if (!active.contains(code)) {
                throw new ApiException(ErrorCode.LANGUAGE_NOT_ACTIVE, "Dil aktiv deyil: " + code);
            }
            AttributeGroupTranslation translation = AttributeGroupTranslation.builder()
                    .group(group).languageCode(code).name(sanitizer.stripTags(name)).build();
            group.getTranslations().put(code, translation);
        });

        AttributeGroup saved = groupRepository.save(group);
        return new AttributeDtos.GroupResponse(
                saved.getId(), saved.getCode(),
                saved.name(language, languageService.defaultLanguageCode()), saved.getSortOrder());
    }

    // =======================================================================
    // Internals
    // =======================================================================

    private void applyScalars(Attribute attribute, AttributeDtos.SaveRequest request) {
        attribute.setUnit(request.unit());
        attribute.setFilterable(Boolean.TRUE.equals(request.filterable()));
        attribute.setShowInListing(Boolean.TRUE.equals(request.showInListing()));
        attribute.setRequired(Boolean.TRUE.equals(request.required()));
        attribute.setStatus(request.status() == null ? ContentStatus.ACTIVE : request.status());
        if (request.sortOrder() != null) {
            attribute.setSortOrder(request.sortOrder());
        }
        attribute.setGroup(request.groupId() == null ? null
                : groupRepository.findById(request.groupId())
                .orElseThrow(() -> ApiException.notFound("Attribut qrupu", request.groupId())));
    }

    private void applyTranslations(Attribute attribute, Map<String, AttributeDtos.TranslationDto> translations) {
        Set<String> active = Set.copyOf(languageService.activeLanguageCodes());
        attribute.getTranslations().keySet().removeIf(code -> !translations.containsKey(code));

        translations.forEach((code, dto) -> {
            if (!active.contains(code)) {
                throw new ApiException(ErrorCode.LANGUAGE_NOT_ACTIVE, "Dil aktiv deyil: " + code);
            }
            AttributeTranslation translation = attribute.getTranslations()
                    .getOrDefault(code, new AttributeTranslation());
            translation.setName(sanitizer.stripTags(dto.name()).trim());
            translation.setDescription(sanitizer.stripTags(dto.description()));
            attribute.putTranslation(code, translation);
        });
    }

    private void applyOptionLabels(AttributeOption option, Map<String, String> labels) {
        Set<String> active = Set.copyOf(languageService.activeLanguageCodes());
        option.getTranslations().keySet().removeIf(code -> !labels.containsKey(code));

        labels.forEach((code, label) -> {
            if (!active.contains(code)) {
                throw new ApiException(ErrorCode.LANGUAGE_NOT_ACTIVE, "Dil aktiv deyil: " + code);
            }
            AttributeOptionTranslation translation = option.getTranslations()
                    .getOrDefault(code, new AttributeOptionTranslation());
            translation.setLabel(sanitizer.stripTags(label).trim());
            option.putTranslation(code, translation);
        });
    }

    private AttributeDtos.AttributeResponse toResponse(Attribute attribute, String language,
                                                       String fallback, boolean withUsage) {
        Map<String, AttributeDtos.TranslationDto> translations = new LinkedHashMap<>();
        attribute.getTranslations().forEach((code, translation) -> translations.put(code,
                new AttributeDtos.TranslationDto(translation.getName(), translation.getDescription())));

        List<AttributeDtos.OptionResponse> options = attribute.getOptions().stream()
                .map(option -> new AttributeDtos.OptionResponse(
                        option.getId(), option.getValue(), option.label(language, fallback),
                        option.getColorHex(), option.getSortOrder(),
                        withUsage ? optionRepository.countUsages(option.getId()) : null))
                .toList();

        var translation = attribute.getTranslations().getOrDefault(language,
                attribute.getTranslations().get(fallback));

        return new AttributeDtos.AttributeResponse(
                attribute.getId(), attribute.getCode(),
                attribute.name(language, fallback),
                translation == null ? null : translation.getDescription(),
                attribute.getDataType(), attribute.getUnit(),
                attribute.isFilterable(), attribute.isShowInListing(), attribute.isRequired(),
                attribute.getStatus(), attribute.getSortOrder(),
                attribute.getGroup() == null ? null : attribute.getGroup().getId(),
                attribute.getGroup() == null ? null : attribute.getGroup().name(language, fallback),
                options, translations);
    }

    private Attribute load(Long id) {
        return repository.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Attribut", id));
    }
}
