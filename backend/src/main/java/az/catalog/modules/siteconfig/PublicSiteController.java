package az.catalog.modules.siteconfig;

import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.modules.language.LanguageDtos;
import az.catalog.modules.language.LanguageService;
import az.catalog.modules.media.MediaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Tag(name = "Public · Sayt")
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@SecurityRequirements
public class PublicSiteController {

    /** Branding fields holding a media id, paired with the URL field the client reads. */
    private static final Map<String, String> BRANDING_URL_FIELDS = Map.of(
            "logoLightMediaId", "logoLightUrl",
            "logoDarkMediaId", "logoDarkUrl",
            "mobileLogoMediaId", "mobileLogoUrl",
            "faviconMediaId", "faviconUrl",
            "defaultOgMediaId", "defaultOgUrl");

    private final SiteConfigurationService siteConfigurationService;
    private final LanguageService languageService;
    private final MediaService mediaService;

    @Operation(summary = "Aktiv şirkət, branding və tema məlumatları",
            description = """
                    Dərc olunmuş konfiqurasiyanı qaytarır. Branding-dəki media id-lərinin
                    URL-ləri də əlavə olunur ki, frontend əlavə sorğu göndərməsin.
                    """)
    @GetMapping("/site-configuration")
    public DataResponse<JsonNode> siteConfiguration() {
        JsonNode published = siteConfigurationService.publishedConfiguration();
        return DataResponse.of(withResolvedBranding(published));
    }

    @Operation(summary = "Aktiv dillər")
    @GetMapping("/languages")
    public DataResponse<List<LanguageDtos.LanguageResponse>> languages() {
        return DataResponse.of(languageService.activeLanguages().stream()
                .map(LanguageDtos.LanguageResponse::from)
                .toList());
    }

    /**
     * Copies the document and adds a resolved URL beside every branding media id.
     * The stored configuration keeps ids (they survive a CDN move); the response carries
     * URLs (the browser cannot resolve an id).
     */
    private JsonNode withResolvedBranding(JsonNode configuration) {
        if (configuration == null || !configuration.isObject()) {
            return configuration;
        }

        ObjectNode copy = (ObjectNode) configuration.deepCopy();
        JsonNode brandingNode = copy.get("branding");
        if (brandingNode == null || !brandingNode.isObject()) {
            return copy;
        }
        ObjectNode branding = (ObjectNode) brandingNode;

        List<Long> mediaIds = new ArrayList<>();
        BRANDING_URL_FIELDS.keySet().forEach(field -> {
            JsonNode value = branding.get(field);
            if (value != null && value.isNumber()) {
                mediaIds.add(value.asLong());
            }
        });

        if (mediaIds.isEmpty()) {
            return copy;
        }

        Map<Long, String> urls = mediaService.publicUrls(mediaIds);
        BRANDING_URL_FIELDS.forEach((idField, urlField) -> {
            JsonNode value = branding.get(idField);
            if (value != null && value.isNumber()) {
                branding.put(urlField, urls.get(value.asLong()));
            }
        });

        return copy;
    }
}
