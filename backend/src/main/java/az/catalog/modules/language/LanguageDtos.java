package az.catalog.modules.language;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class LanguageDtos {

    private LanguageDtos() {
    }

    public record LanguageResponse(
            String code,
            String name,
            String nativeName,
            String flagCode,
            boolean isDefault,
            boolean isActive,
            int sortOrder
    ) {
        public static LanguageResponse from(Language language) {
            return new LanguageResponse(
                    language.getCode(),
                    language.getName(),
                    language.getNativeName(),
                    language.getFlagCode(),
                    language.isDefault(),
                    language.isActive(),
                    language.getSortOrder()
            );
        }
    }

    public record SaveRequest(
            @NotBlank
            @Pattern(regexp = "[a-z]{2}(-[a-z]{2})?", message = "Dil kodu ISO formatında olmalıdır (məs: az, en, ru)")
            String code,

            @NotBlank @Size(max = 64)
            String name,

            @Size(max = 64)
            String nativeName,

            @Size(max = 8)
            String flagCode,

            Boolean active,
            Integer sortOrder
    ) {
    }
}
