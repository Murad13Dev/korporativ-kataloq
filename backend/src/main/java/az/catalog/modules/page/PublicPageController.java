package az.catalog.modules.page;

import az.catalog.common.config.RequestLanguage;
import az.catalog.common.dto.ApiResponses.DataResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Public · Səhifələr")
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@SecurityRequirements
public class PublicPageController {

    private final PublicPageService service;

    @Operation(summary = "Ana səhifə", description = "HOME kodlu səhifənin dərc olunmuş versiyası.")
    @GetMapping("/home")
    public DataResponse<PageDtos.PublicPage> home(@RequestLanguage String language) {
        return DataResponse.of(service.byCode("HOME", language));
    }

    @Operation(summary = "Səhifə və bölmələr",
            description = "Yalnız görünən bölmələr qaytarılır — gizlədilmiş bölmələr filtrlənir.")
    @GetMapping("/pages/{slug}")
    public DataResponse<PageDtos.PublicPage> bySlug(@PathVariable String slug,
                                                    @RequestLanguage String language) {
        return DataResponse.of(service.bySlug(slug, language));
    }

    @Operation(summary = "Kod ilə səhifə", description = "Sabit səhifələr üçün: CONTACT, ABOUT, PRIVACY.")
    @GetMapping("/pages/by-code/{code}")
    public DataResponse<PageDtos.PublicPage> byCode(@PathVariable String code,
                                                    @RequestLanguage String language) {
        return DataResponse.of(service.byCode(code, language));
    }
}
