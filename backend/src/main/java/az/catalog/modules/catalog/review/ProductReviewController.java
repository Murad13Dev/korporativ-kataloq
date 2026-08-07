package az.catalog.modules.catalog.review;

import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.dto.ApiResponses.PageResponse;
import az.catalog.common.exception.ApiException;
import az.catalog.modules.catalog.product.Product;
import az.catalog.modules.catalog.product.ProductRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Public · Rəylər")
@RestController
@RequestMapping("/api/public/products/{slug}/reviews")
@RequiredArgsConstructor
@SecurityRequirements
public class ProductReviewController {

    private final ProductReviewService service;
    private final ProductRepository productRepository;

    @Operation(summary = "Təsdiqlənmiş rəylər")
    @GetMapping
    public PageResponse<ReviewDtos.ReviewDto> list(
            @PathVariable String slug,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.list(resolveProductId(slug), pageable));
    }

    @Operation(summary = "Reytinq xülasəsi", description = "Orta bal, say və ulduz üzrə bölgü")
    @GetMapping("/summary")
    public DataResponse<ReviewDtos.RatingSummary> summary(@PathVariable String slug) {
        return DataResponse.of(service.summary(resolveProductId(slug)));
    }

    private Long resolveProductId(String slug) {
        Product product = productRepository.findPublishedBySlug(slug)
                .orElseThrow(() -> ApiException.notFoundBySlug("Məhsul", slug));
        return product.getId();
    }
}
