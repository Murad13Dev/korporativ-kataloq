package az.catalog.modules.auth;

import az.catalog.modules.user.User;
import az.catalog.modules.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(max = 128) String password
    ) {
    }

    public record LoginResponse(
            String accessToken,
            long expiresIn,
            AuthenticatedUser user
    ) {
    }

    public record AuthenticatedUser(
            Long id,
            String firstName,
            String lastName,
            String email,
            UserRole role,
            String locale,
            Long avatarMediaId
    ) {
        public static AuthenticatedUser from(User user) {
            return new AuthenticatedUser(
                    user.getId(), user.getFirstName(), user.getLastName(),
                    user.getEmail(), user.getRole(), user.getLocale(), user.getAvatarMediaId());
        }
    }

    public record ForgotPasswordRequest(
            @NotBlank @Email @Size(max = 190) String email
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 10, max = 128) String password
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 10, max = 128) String newPassword
    ) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @Size(max = 40) String phone,
            @Size(max = 8) String locale,
            Long avatarMediaId
    ) {
    }
}
