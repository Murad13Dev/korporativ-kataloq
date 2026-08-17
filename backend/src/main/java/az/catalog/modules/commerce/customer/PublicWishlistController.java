package az.catalog.modules.commerce.customer;

import az.catalog.common.config.RequestLanguage;
import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.modules.catalog.product.ProductDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Public · Paylaşılan sevimlilər",
        description = "Bir müştərinin sevimlilər siyahısına, onun paylaşdığı token vasitəsilə anonim baxış.")
@RestController
@RequestMapping("/api/public/wishlist")
@RequiredArgsConstructor
@SecurityRequirements
public class PublicWishlistController {

    private final WishlistService service;

    @Operation(summary = "Paylaşılan siyahıya baxış",
            description = "Naməlum token boş siyahı kimi qaytarılır — mövcud olub-olmadığı ayrıca açıqlanmır.")
    @GetMapping("/{token}")
    public DataResponse<List<ProductDtos.ProductCard>> view(
            @PathVariable String token, @RequestLanguage String language) {
        return DataResponse.of(service.byShareToken(token, language));
    }
}
