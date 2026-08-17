package az.catalog.modules.commerce.customer;

import az.catalog.common.config.RequestLanguage;
import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.security.SecurityUtils;
import az.catalog.modules.catalog.product.ProductDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Customer · Sevimlilər")
@RestController
@RequestMapping("/api/customer/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService service;

    @Operation(summary = "Sevimlilər siyahısı")
    @GetMapping
    public DataResponse<List<ProductDtos.ProductCard>> list(@RequestLanguage String language) {
        return DataResponse.of(service.list(SecurityUtils.requireCustomer().id(), language));
    }

    @Operation(summary = "Məhsulu sevimlilərə əlavə etmək")
    @PostMapping("/{productId}")
    public ResponseEntity<Void> add(@PathVariable Long productId) {
        service.add(SecurityUtils.requireCustomer().id(), productId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Məhsulu sevimlilərdən çıxarmaq")
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(@PathVariable Long productId) {
        service.remove(SecurityUtils.requireCustomer().id(), productId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Paylaşım linki",
            description = "İlk çağırışda yaradılır, sonra dəyişmir — /api/public/wishlist/{token} ilə açıqdır.")
    @PostMapping("/share")
    public DataResponse<String> share() {
        return DataResponse.of(service.getOrCreateShareToken(SecurityUtils.requireCustomer().id()));
    }
}
