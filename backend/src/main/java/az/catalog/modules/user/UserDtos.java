package az.catalog.modules.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserResponse(
            Long id,
            String firstName,
            String lastName,
            String email,
            String phone,
            UserRole role,
            UserStatus status,
            String locale,
            Long avatarMediaId,
            OffsetDateTime lastLoginAt,
            OffsetDateTime createdAt
    ) {
        public static UserResponse from(User user) {
            return new UserResponse(
                    user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                    user.getPhone(), user.getRole(), user.getStatus(), user.getLocale(),
                    user.getAvatarMediaId(), user.getLastLoginAt(), user.getCreatedAt());
        }
    }

    public record CreateRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotBlank @Email @Size(max = 190) String email,
            @Size(max = 40) String phone,
            @NotNull UserRole role,
            /** Optional: when omitted a random password is generated and e-mailed. */
            @Size(min = 10, max = 128) String password,
            @Size(max = 8) String locale
    ) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotBlank @Email @Size(max = 190) String email,
            @Size(max = 40) String phone,
            @Size(max = 8) String locale,
            Long avatarMediaId
    ) {
    }

    public record StatusRequest(@NotNull UserStatus status) {
    }

    public record RoleRequest(@NotNull UserRole role) {
    }

    public record SetPasswordRequest(@NotBlank @Size(min = 10, max = 128) String password) {
    }
}
