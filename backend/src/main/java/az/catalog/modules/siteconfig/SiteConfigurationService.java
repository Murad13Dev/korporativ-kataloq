package az.catalog.modules.siteconfig;

import az.catalog.common.config.CacheConfig;
import az.catalog.common.domain.VersionStatus;
import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.common.security.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Draft → preview → publish → rollback for the whole site configuration.
 *
 * Invariants (also enforced by partial unique indexes):
 * <ul>
 *   <li>at most one PUBLISHED version</li>
 *   <li>at most one DRAFT version</li>
 *   <li>publishing archives the previous PUBLISHED version rather than deleting it</li>
 *   <li>restoring an old version creates a new DRAFT — it never publishes directly</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteConfigurationService {

    /**
     * Colour-ish theme values are echoed into CSS custom properties by the frontend, so
     * they are validated on write rather than trusted at render time.
     */
    private static final Set<String> COLOR_FIELDS = Set.of(
            "primaryColor", "primaryHoverColor", "primaryContrastColor", "secondaryColor",
            "accentColor", "saleColor", "successColor", "warningColor", "dangerColor",
            "backgroundColor", "sectionBackgroundColor", "headingColor", "textColor",
            "mutedTextColor", "borderColor", "footerBackgroundColor", "footerTextColor");

    private static final Map<String, int[]> NUMERIC_BOUNDS = Map.of(
            "buttonRadius", new int[]{0, 40},
            "cardRadius", new int[]{0, 40},
            "inputRadius", new int[]{0, 40},
            "containerWidth", new int[]{960, 1920},
            "baseFontSize", new int[]{12, 20});

    private final SiteConfigurationRepository repository;
    private final ObjectMapper objectMapper;

    // -----------------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------------

    /**
     * The live configuration, cached because every storefront request needs it.
     * Invalidated on publish and on restore.
     */
    @Cacheable(CacheConfig.SITE_CONFIG)
    @Transactional(readOnly = true)
    public JsonNode publishedConfiguration() {
        return repository.findFirstByStatus(VersionStatus.PUBLISHED)
                .map(SiteConfigurationVersion::getConfiguration)
                .orElseGet(objectMapper::createObjectNode);
    }

    public SiteConfigSnapshot publishedSnapshot() {
        return new SiteConfigSnapshot(publishedConfiguration());
    }

    @Transactional(readOnly = true)
    public SiteConfigDtos.VersionResponse published() {
        return repository.findFirstByStatus(VersionStatus.PUBLISHED)
                .map(SiteConfigDtos.VersionResponse::from)
                .orElseThrow(() -> ApiException.notFound("Dərc olunmuş konfiqurasiya", "PUBLISHED"));
    }

    @Transactional(readOnly = true)
    public SiteConfigDtos.VersionResponse draft() {
        return repository.findFirstByStatus(VersionStatus.DRAFT)
                .map(SiteConfigDtos.VersionResponse::from)
                .orElseThrow(() -> ApiException.notFound("Draft konfiqurasiya", "DRAFT"));
    }

    /**
     * What the site would look like if the draft were published right now.
     * Falls back to the published document when no draft exists.
     */
    @Transactional(readOnly = true)
    public JsonNode preview() {
        return repository.findFirstByStatus(VersionStatus.DRAFT)
                .map(SiteConfigurationVersion::getConfiguration)
                .orElseGet(this::publishedConfiguration);
    }

    @Transactional(readOnly = true)
    public Page<SiteConfigDtos.VersionSummary> versions(Pageable pageable) {
        return repository.findAllByOrderByVersionNumberDesc(pageable)
                .map(SiteConfigDtos.VersionSummary::from);
    }

    @Transactional(readOnly = true)
    public SiteConfigDtos.VersionResponse version(Long id) {
        return SiteConfigDtos.VersionResponse.from(load(id));
    }

    // -----------------------------------------------------------------------
    // Draft lifecycle
    // -----------------------------------------------------------------------

    /** Opens a draft seeded from the published version. Returns the existing draft if one is open. */
    @Transactional
    public SiteConfigDtos.VersionResponse createDraft(String label) {
        var existing = repository.findFirstByStatus(VersionStatus.DRAFT);
        if (existing.isPresent()) {
            return SiteConfigDtos.VersionResponse.from(existing.get());
        }
        JsonNode base = publishedConfiguration().deepCopy();
        SiteConfigurationVersion draft = repository.save(SiteConfigurationVersion.builder()
                .versionNumber(repository.maxVersionNumber() + 1)
                .status(VersionStatus.DRAFT)
                .label(label)
                .configuration(base)
                .build());
        return SiteConfigDtos.VersionResponse.from(draft);
    }

    /**
     * Deep-merges the supplied fragment into the draft, so the admin panel can save one
     * settings tab at a time without resending the entire document.
     */
    @Transactional
    public SiteConfigDtos.VersionResponse updateDraft(SiteConfigDtos.UpdateDraftRequest request) {
        SiteConfigurationVersion draft = repository.findFirstByStatus(VersionStatus.DRAFT)
                .orElseGet(() -> {
                    createDraft(null);
                    return repository.findFirstByStatus(VersionStatus.DRAFT).orElseThrow();
                });

        JsonNode incoming = request.configuration();
        validate(incoming);

        ObjectNode merged = draft.getConfiguration().deepCopy();
        deepMerge(merged, incoming);
        draft.setConfiguration(merged);
        if (request.label() != null) {
            draft.setLabel(request.label());
        }
        return SiteConfigDtos.VersionResponse.from(draft);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.SITE_CONFIG, allEntries = true)
    public SiteConfigDtos.VersionResponse publish() {
        SiteConfigurationVersion draft = repository.findFirstByStatus(VersionStatus.DRAFT)
                .orElseThrow(() -> new ApiException(ErrorCode.NO_DRAFT_TO_PUBLISH));

        validate(draft.getConfiguration());

        // Archive first and flush: the partial unique index allows only one PUBLISHED row.
        repository.findFirstByStatus(VersionStatus.PUBLISHED).ifPresent(current -> {
            current.setStatus(VersionStatus.ARCHIVED);
            repository.save(current);
        });
        repository.flush();

        draft.setStatus(VersionStatus.PUBLISHED);
        draft.setPublishedAt(OffsetDateTime.now());
        draft.setPublishedBy(SecurityUtils.currentAdminId().orElse(null));

        log.info("Published site configuration version {}", draft.getVersionNumber());
        return SiteConfigDtos.VersionResponse.from(draft);
    }

    /** Discards the open draft. The published site is untouched. */
    @Transactional
    public void discardDraft() {
        repository.findFirstByStatus(VersionStatus.DRAFT).ifPresent(repository::delete);
    }

    /**
     * Copies an archived version into a new draft. Restoring must go through review, so
     * this deliberately does not publish.
     */
    @Transactional
    public SiteConfigDtos.VersionResponse restore(Long versionId) {
        SiteConfigurationVersion source = load(versionId);
        if (source.getStatus() == VersionStatus.DRAFT) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE, "Draft versiya bərpa edilə bilməz");
        }
        repository.findFirstByStatus(VersionStatus.DRAFT).ifPresent(repository::delete);
        repository.flush();

        SiteConfigurationVersion draft = repository.save(SiteConfigurationVersion.builder()
                .versionNumber(repository.maxVersionNumber() + 1)
                .status(VersionStatus.DRAFT)
                .label("v%d əsasında bərpa".formatted(source.getVersionNumber()))
                .configuration(source.getConfiguration().deepCopy())
                .build());

        log.info("Restored site configuration v{} into new draft v{}",
                source.getVersionNumber(), draft.getVersionNumber());
        return SiteConfigDtos.VersionResponse.from(draft);
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private SiteConfigurationVersion load(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Konfiqurasiya versiyası", id));
    }

    /**
     * Merges {@code patch} into {@code target}. Objects merge recursively; arrays and
     * scalars replace wholesale, which is what an admin expects when reordering a list.
     */
    private void deepMerge(ObjectNode target, JsonNode patch) {
        if (patch == null || !patch.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = patch.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode existing = target.get(entry.getKey());
            if (existing != null && existing.isObject() && entry.getValue().isObject()) {
                deepMerge((ObjectNode) existing, entry.getValue());
            } else {
                target.set(entry.getKey(), entry.getValue());
            }
        }
    }

    private void validate(JsonNode configuration) {
        if (configuration == null || !configuration.isObject()) {
            throw ApiException.badRequest("Konfiqurasiya JSON obyekti olmalıdır");
        }
        JsonNode theme = configuration.get("theme");
        if (theme == null) {
            return;
        }
        for (String field : COLOR_FIELDS) {
            JsonNode value = theme.get(field);
            if (value != null && !value.isNull()
                    && !value.asText("").matches("#[0-9a-fA-F]{3}([0-9a-fA-F]{3}([0-9a-fA-F]{2})?)?")) {
                throw ApiException.field(ErrorCode.VALIDATION_ERROR, "theme." + field,
                        "Rəng #RRGGBB formatında olmalıdır");
            }
        }
        NUMERIC_BOUNDS.forEach((field, bounds) -> {
            JsonNode value = theme.get(field);
            if (value != null && value.isNumber()) {
                int number = value.asInt();
                if (number < bounds[0] || number > bounds[1]) {
                    throw ApiException.field(ErrorCode.VALIDATION_ERROR, "theme." + field,
                            "Dəyər %d–%d aralığında olmalıdır".formatted(bounds[0], bounds[1]));
                }
            }
        });
    }
}
