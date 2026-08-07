package az.catalog.modules.catalog.review;

import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Customer · Rəylər")
@RestController
@RequestMapping("/api/customer/reviews")
@RequiredArgsConstructor
public class CustomerReviewController {

    private final ProductReviewService service;

    @Operation(summary = "Rəy yazmaq", description = "Hər müştəri bir məhsula yalnız bir rəy yaza bilər; rəy admin təsdiqindən sonra görünür.")
    @PostMapping
    public DataResponse<ReviewDtos.ReviewDto> create(@Valid @RequestBody ReviewDtos.CreateReviewRequest request) {
        return DataResponse.of(service.create(SecurityUtils.requireCustomer().id(), request));
    }
}
