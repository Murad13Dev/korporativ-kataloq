package az.catalog.modules.commerce.loyalty;

import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.dto.ApiResponses.PageResponse;
import az.catalog.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Customer · Loyallıq")
@RestController
@RequestMapping("/api/customer/loyalty")
@RequiredArgsConstructor
public class CustomerLoyaltyController {

    private final LoyaltyService service;

    @Operation(summary = "Xal balansı")
    @GetMapping
    public DataResponse<LoyaltyDtos.BalanceDto> balance() {
        return DataResponse.of(service.balance(SecurityUtils.requireCustomer().id()));
    }

    @Operation(summary = "Xal tarixçəsi")
    @GetMapping("/history")
    public PageResponse<LoyaltyDtos.TransactionDto> history(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.history(SecurityUtils.requireCustomer().id(), pageable));
    }

    @Operation(summary = "Xalları promo koda çevirmək",
            description = "100-ün mislinə bərabər xal tələb olunur; hər 100 xal 5 manat endirimə çevrilir.")
    @PostMapping("/redeem")
    public DataResponse<LoyaltyDtos.RedeemResponse> redeem(@Valid @RequestBody LoyaltyDtos.RedeemRequest request) {
        return DataResponse.of(service.redeem(SecurityUtils.requireCustomer().id(), request.points()));
    }
}
