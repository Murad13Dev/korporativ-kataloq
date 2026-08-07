package az.catalog.modules.language;

import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LanguageService {

    public static final String CACHE = "languages";

    private final LanguageRepository repository;

    @Cacheable(value = CACHE, key = "'active'")
    @Transactional(readOnly = true)
    public List<Language> activeLanguages() {
        return repository.findAllByIsActiveTrueOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<Language> allLanguages() {
        return repository.findAllByOrderBySortOrderAsc();
    }

    @Cacheable(value = CACHE, key = "'default'")
    @Transactional(readOnly = true)
    public String defaultLanguageCode() {
        return repository.findByIsDefaultTrue()
                .map(Language::getCode)
                .orElse("az");
    }

    @Cacheable(value = CACHE, key = "'codes'")
    @Transactional(readOnly = true)
    public List<String> activeLanguageCodes() {
        return repository.findAllByIsActiveTrueOrderBySortOrderAsc().stream().map(Language::getCode).toList();
    }

    /**
     * Falls back to the default language when the requested code is unknown or inactive,
     * so a bad {@code Accept-Language} header never produces an empty page.
     */
    @Transactional(readOnly = true)
    public String resolveOrDefault(String requested) {
        if (requested != null && !requested.isBlank()
                && activeLanguageCodes().contains(requested.toLowerCase())) {
            return requested.toLowerCase();
        }
        return defaultLanguageCode();
    }

    @Transactional(readOnly = true)
    public void requireActive(String code) {
        if (!activeLanguageCodes().contains(code)) {
            throw new ApiException(ErrorCode.LANGUAGE_NOT_ACTIVE, "Dil aktiv deyil: " + code);
        }
    }

    @Transactional
    @CacheEvict(value = CACHE, allEntries = true)
    public Language create(LanguageDtos.SaveRequest request) {
        String code = request.code().toLowerCase();
        if (repository.existsById(code)) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "Bu dil kodu artıq mövcuddur: " + code);
        }
        Language language = Language.builder()
                .code(code)
                .name(request.name())
                .nativeName(request.nativeName())
                .flagCode(request.flagCode())
                .isActive(request.active() == null || request.active())
                .isDefault(false)
                .sortOrder(request.sortOrder() == null ? repository.findAll().size() + 1 : request.sortOrder())
                .build();
        return repository.save(language);
    }

    @Transactional
    @CacheEvict(value = CACHE, allEntries = true)
    public Language update(String code, LanguageDtos.SaveRequest request) {
        Language language = get(code);
        language.setName(request.name());
        language.setNativeName(request.nativeName());
        language.setFlagCode(request.flagCode());
        if (request.sortOrder() != null) {
            language.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            setActiveInternal(language, request.active());
        }
        return language;
    }

    @Transactional
    @CacheEvict(value = CACHE, allEntries = true)
    public Language setActive(String code, boolean active) {
        Language language = get(code);
        setActiveInternal(language, active);
        return language;
    }

    private void setActiveInternal(Language language, boolean active) {
        if (!active) {
            if (language.isDefault()) {
                throw new ApiException(ErrorCode.CANNOT_DEACTIVATE_DEFAULT_LANGUAGE);
            }
            if (repository.countByIsActiveTrue() <= 1) {
                throw new ApiException(ErrorCode.CANNOT_DEACTIVATE_DEFAULT_LANGUAGE,
                        "Ən azı bir dil aktiv qalmalıdır");
            }
        }
        language.setActive(active);
    }

    /**
     * Promotes a language to the site default. Demoting the previous default in the same
     * transaction keeps the {@code uq_languages_single_default} partial index satisfied.
     */
    @Transactional
    @CacheEvict(value = CACHE, allEntries = true)
    public Language makeDefault(String code) {
        Language language = get(code);
        if (!language.isActive()) {
            throw new ApiException(ErrorCode.LANGUAGE_NOT_ACTIVE);
        }
        repository.findByIsDefaultTrue().ifPresent(current -> current.setDefault(false));
        repository.flush();
        language.setDefault(true);
        return language;
    }

    @Transactional
    @CacheEvict(value = CACHE, allEntries = true)
    public void delete(String code) {
        Language language = get(code);
        if (language.isDefault()) {
            throw new ApiException(ErrorCode.CANNOT_DEACTIVATE_DEFAULT_LANGUAGE,
                    "Əsas dil silinə bilməz");
        }
        // Translation rows cascade at database level (ON DELETE CASCADE on language_code FKs).
        repository.delete(language);
    }

    @Transactional(readOnly = true)
    public Language get(String code) {
        return repository.findById(code.toLowerCase())
                .orElseThrow(() -> ApiException.notFound("Dil", code));
    }
}
