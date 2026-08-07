package az.catalog.modules.dashboard;

import az.catalog.common.config.RequestLanguage;
import az.catalog.common.dto.ApiResponses.DataResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin · Dashboard")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @Operation(summary = "Ümumi göstəricilər",
            description = """
                    Yalnız sistemin real olaraq sahib olduğu məlumatlar qaytarılır.
                    Analytics sistemi olmadan trafik və gəlir qrafiki göstərilmir.
                    """)
    @GetMapping("/summary")
    public DataResponse<DashboardDtos.Summary> summary(@RequestLanguage String language) {
        return DataResponse.of(service.summary(language));
    }
}
