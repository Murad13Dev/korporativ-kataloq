package az.catalog.modules.commerce.company;

import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Customer · Şirkət")
@RestController
@RequestMapping("/api/customer/company")
@RequiredArgsConstructor
public class CustomerCompanyController {

    private final CompanyService service;

    @Operation(summary = "Mənim şirkətim", description = "Şirkət üzvü deyilsinizsə data null qayıdır.")
    @GetMapping
    public DataResponse<CompanyDtos.CompanyView> mine() {
        return DataResponse.of(service.myCompany(SecurityUtils.requireCustomer().id()).orElse(null));
    }

    @Operation(summary = "Şirkət hesabı yaratmaq", description = "Yaradan şəxs avtomatik SAHİB olur.")
    @PostMapping
    public DataResponse<CompanyDtos.CompanyView> create(@Valid @RequestBody CompanyDtos.CreateCompanyRequest request) {
        return DataResponse.of(service.create(SecurityUtils.requireCustomer().id(), request));
    }

    @Operation(summary = "Üzv dəvət etmək", description = "Yalnız şirkət sahibi. Dəvət olunan əvvəlcədən qeydiyyatdan keçmiş olmalıdır.")
    @PostMapping("/members")
    public DataResponse<CompanyDtos.MemberView> invite(@Valid @RequestBody CompanyDtos.InviteMemberRequest request) {
        return DataResponse.of(service.inviteMember(SecurityUtils.requireCustomer().id(), request));
    }

    @Operation(summary = "Üzvü çıxarmaq", description = "Yalnız şirkət sahibi; sahibin özü çıxarıla bilməz.")
    @DeleteMapping("/members/{memberId}")
    public ResponseEntity<Void> remove(@PathVariable Long memberId) {
        service.removeMember(SecurityUtils.requireCustomer().id(), memberId);
        return ResponseEntity.noContent().build();
    }
}
