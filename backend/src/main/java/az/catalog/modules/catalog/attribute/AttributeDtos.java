package az.catalog.modules.catalog.attribute;

import az.catalog.common.domain.ContentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class AttributeDtos {

    private AttributeDtos() {
    }

    /**
     * Everything the product editor needs to render one specification input:
     * which control to draw ({@code dataType}) and, for pickers, what to draw it with.
     */
    public record AttributeResponse(
            Long id,
            String code,
            String name,
            String description,
            AttributeDataType dataType,
            String unit,
            boolean filterable,
            boolean showInListing,
            boolean required,
            ContentStatus status,
            int sortOrder,
            Long groupId,
            String groupName,
            List<OptionResponse> options,
            Map<String, TranslationDto> translations
    ) {
    }

    public record OptionResponse(
            Long id,
            String value,
            String label,
            String colorHex,
            int sortOrder,
            /** Populated on the admin detail view so an in-use option is not deleted blindly. */
            Long usageCount
    ) {
    }

    public record TranslationDto(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 500) String description
    ) {
    }

    public record SaveRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9_]{2,64}",
                    message = "Kod yalnız kiçik hərf, rəqəm və alt xətt ola bilər")
            String code,

            @NotNull AttributeDataType dataType,
            @Size(max = 24) String unit,
            Long groupId,

            Boolean filterable,
            Boolean showInListing,
            Boolean required,
            ContentStatus status,
            Integer sortOrder,

            @NotEmpty(message = "Ən azı bir dil üçün ad tələb olunur")
            Map<String, @Valid TranslationDto> translations
    ) {
    }

    public record OptionRequest(
            @NotBlank @Size(max = 160) String value,
            @Pattern(regexp = "#[0-9a-fA-F]{3,8}", message = "Rəng #RRGGBB formatında olmalıdır")
            String colorHex,
            Integer sortOrder,
            @NotEmpty(message = "Ən azı bir dil üçün etiket tələb olunur")
            Map<String, @Size(max = 160) String> labels
    ) {
    }

    public record StatusRequest(@NotNull ContentStatus status) {
    }

    public record GroupResponse(Long id, String code, String name, int sortOrder) {
    }

    public record GroupRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9_]{2,64}") String code,
            Integer sortOrder,
            @NotEmpty Map<String, @Size(max = 160) String> names
    ) {
    }
}
