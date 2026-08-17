package az.catalog.modules.dashboard;

import az.catalog.common.config.RequestLanguage;
import az.catalog.common.dto.ApiResponses.DataResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin · Dashboard")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;
    private final TranslationCoverageService translationCoverageService;

    @Operation(summary = "Ümumi göstəricilər",
            description = """
                    Yalnız sistemin real olaraq sahib olduğu məlumatlar qaytarılır.
                    Analytics sistemi olmadan trafik və gəlir qrafiki göstərilmir.
                    """)
    @GetMapping("/summary")
    public DataResponse<DashboardDtos.Summary> summary(@RequestLanguage String language) {
        return DataResponse.of(service.summary(language));
    }

    @Operation(summary = "Canlı ziyarətçilər",
            description = "Hazırda məhsul səhifələrində olan ziyarətçilər — yaddaşdan, saxlanılmır.")
    @GetMapping("/live-visitors")
    public DataResponse<DashboardDtos.LiveVisitorsSnapshot> liveVisitors(@RequestLanguage String language) {
        return DataResponse.of(service.liveVisitors(language));
    }

    @Operation(summary = "Anbar radar",
            description = "Son 30 günün satış sürətinə görə, ən tez bitəcək məhsullar — statik anbar azlığından fərqli, irəliyə proqnoz.")
    @GetMapping("/stock-forecast")
    public DataResponse<List<DashboardDtos.StockForecastItem>> stockForecast(@RequestLanguage String language) {
        return DataResponse.of(service.stockForecast(language));
    }

    @Operation(summary = "Tərcümə tamlığı",
            description = "Standart dildən başqa hər aktiv dil üçün məhsul/kateqoriya/bloq/səhifə tərcümə əhatəsi.")
    @GetMapping("/translation-coverage")
    public DataResponse<List<DashboardDtos.LanguageCoverage>> translationCoverage() {
        return DataResponse.of(translationCoverageService.coverage());
    }
}
