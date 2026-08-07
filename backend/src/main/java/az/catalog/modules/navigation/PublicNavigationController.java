package az.catalog.modules.navigation;

import az.catalog.common.config.RequestLanguage;
import az.catalog.common.dto.ApiResponses.DataResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Public · Naviqasiya")
@RestController
@RequestMapping("/api/public/navigation")
@RequiredArgsConstructor
@SecurityRequirements
public class PublicNavigationController {

    private final MenuService service;

    @Operation(summary = "Menyu (yerə görə)",
            description = """
                    `location`: HEADER · FOOTER_PRIMARY · FOOTER_SECONDARY · MOBILE · LEGAL

                    Yalnız dərc olunmuş versiya və görünən elementlər qaytarılır.
                    """)
    @GetMapping("/{location}")
    public DataResponse<List<MenuDtos.PublicMenuItem>> byLocation(
            @Parameter(description = "Menyunun yeri") @PathVariable Menu.Location location,
            @RequestLanguage String language) {
        return DataResponse.of(service.publicMenu(location, language));
    }

    /**
     * All menus in one call. The layout renders header and footer together on first
     * paint, so fetching them separately would just add round trips.
     */
    @Operation(summary = "Bütün menyular", description = "Header və footer bir sorğuda.")
    @GetMapping
    public DataResponse<Map<String, List<MenuDtos.PublicMenuItem>>> all(
            @RequestLanguage String language) {
        Map<String, List<MenuDtos.PublicMenuItem>> menus = new LinkedHashMap<>();
        for (Menu.Location location : Menu.Location.values()) {
            menus.put(location.name(), service.publicMenu(location, language));
        }
        return DataResponse.of(menus);
    }
}
