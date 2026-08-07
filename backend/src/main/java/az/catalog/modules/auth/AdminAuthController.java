package az.catalog.modules.auth;

import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

@Tag(name = "Admin · Authentication")
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;

    @Operation(summary = "Admin girişi")
    @SecurityRequirements
    @PostMapping("/login")
    public DataResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                                      HttpServletRequest httpRequest,
                                                      HttpServletResponse httpResponse) {
        return DataResponse.of(authService.login(request, httpRequest, httpResponse));
    }

    @Operation(summary = "Access token-i yeniləmək (refresh cookie ilə)")
    @SecurityRequirements
    @PostMapping("/refresh")
    public DataResponse<AuthDtos.LoginResponse> refresh(HttpServletRequest httpRequest,
                                                        HttpServletResponse httpResponse) {
        return DataResponse.of(authService.refresh(httpRequest, httpResponse));
    }

    @Operation(summary = "Çıxış")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authService.logout(httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Bütün cihazlardan çıxış")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(HttpServletResponse httpResponse) {
        authService.logoutAllSessions(SecurityUtils.requireAdmin().id(), httpResponse);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Cari istifadəçi")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public DataResponse<AuthDtos.AuthenticatedUser> me() {
        return DataResponse.of(authService.currentUser(SecurityUtils.requireAdmin().id()));
    }

    @Operation(summary = "Profil məlumatlarını dəyişmək")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/me")
    public DataResponse<AuthDtos.AuthenticatedUser> updateProfile(
            @Valid @RequestBody AuthDtos.UpdateProfileRequest request) {
        return DataResponse.of(authService.updateProfile(SecurityUtils.requireAdmin().id(), request));
    }

    @Operation(summary = "Şifrəni dəyişmək")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request,
                                               HttpServletResponse httpResponse) {
        authService.changePassword(SecurityUtils.requireAdmin().id(), request, httpResponse);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Şifrə bərpası tələb etmək",
            description = "Təhlükəsizlik səbəbindən e-poçtun mövcudluğundan asılı olmayaraq həmişə 204 qaytarır.")
    @SecurityRequirements
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest request,
                                               HttpServletRequest httpRequest) {
        authService.requestPasswordReset(request, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Şifrəni bərpa etmək")
    @SecurityRequirements
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }
}
