package az.catalog.modules.commerce.customer;

import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Customer · Hesab")
@RestController
@RequestMapping("/api/customer/auth")
@RequiredArgsConstructor
public class CustomerAuthController {

    private final CustomerAuthService authService;

    @Operation(summary = "Qeydiyyat",
            description = "Qonaq kimi sifariş vermiş e-poçt varsa həmin qeyd hesaba çevrilir — sifariş tarixçəsi qorunur.")
    @SecurityRequirements
    @PostMapping("/register")
    public DataResponse<CustomerDtos.AuthResponse> register(
            @Valid @RequestBody CustomerDtos.RegisterRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return DataResponse.of(authService.register(request, httpRequest, httpResponse));
    }

    @Operation(summary = "Giriş", description = "cartSessionId göndərilsə qonaq səbəti hesaba birləşdirilir.")
    @SecurityRequirements
    @PostMapping("/login")
    public DataResponse<CustomerDtos.AuthResponse> login(
            @Valid @RequestBody CustomerDtos.LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return DataResponse.of(authService.login(request, httpRequest, httpResponse));
    }

    @Operation(summary = "Token yeniləmək")
    @SecurityRequirements
    @PostMapping("/refresh")
    public DataResponse<CustomerDtos.AuthResponse> refresh(HttpServletRequest httpRequest,
                                                           HttpServletResponse httpResponse) {
        return DataResponse.of(authService.refresh(httpRequest, httpResponse));
    }

    @Operation(summary = "Çıxış")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authService.logout(httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Cari müştəri")
    @GetMapping("/me")
    public DataResponse<CustomerDtos.CustomerProfile> me() {
        return DataResponse.of(authService.profile(SecurityUtils.requireCustomer().id()));
    }

    @Operation(summary = "Profili dəyişmək")
    @PutMapping("/me")
    public DataResponse<CustomerDtos.CustomerProfile> updateProfile(
            @Valid @RequestBody CustomerDtos.UpdateProfileRequest request) {
        return DataResponse.of(
                authService.updateProfile(SecurityUtils.requireCustomer().id(), request));
    }

    @Operation(summary = "Şifrəni dəyişmək")
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody CustomerDtos.ChangePasswordRequest request,
                                               HttpServletResponse httpResponse) {
        authService.changePassword(SecurityUtils.requireCustomer().id(), request, httpResponse);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Şifrə bərpası tələb etmək",
            description = "Təhlükəsizlik səbəbindən e-poçtun mövcudluğundan asılı olmayaraq həmişə 204 qaytarır.")
    @SecurityRequirements
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody CustomerDtos.ForgotPasswordRequest request,
                                               HttpServletRequest httpRequest) {
        authService.requestPasswordReset(request, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Şifrəni bərpa etmək")
    @SecurityRequirements
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody CustomerDtos.ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }
}
