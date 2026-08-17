package az.catalog.modules.commerce.customer;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    // --- auth ---------------------------------------------------------------

    public record RegisterRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotBlank @Email @Size(max = 190) String email,
            @Size(max = 40) String phone,
            @NotBlank @Size(min = 10, max = 128) String password,
            @Size(max = 160) String companyName,
            Boolean marketingOptIn,
            /** Guest cart to fold into the new account. */
            @Size(max = 64) String cartSessionId,
            @AssertTrue(message = "İstifadə şərtləri qəbul edilməlidir")
            boolean termsAccepted,
            /** Another customer's referral code, from a `?ref=` link. An unknown or
                self-referral code is silently ignored rather than rejected. */
            @Size(max = 16) String referralCode
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(max = 128) String password,
            @Size(max = 64) String cartSessionId
    ) {
    }

    public record AuthResponse(
            String accessToken,
            long expiresIn,
            CustomerProfile customer
    ) {
    }

    public record CustomerProfile(
            Long id,
            String firstName,
            String lastName,
            String email,
            String phone,
            String companyName,
            Customer.Type customerType,
            String locale,
            boolean emailVerified,
            boolean marketingOptIn,
            OffsetDateTime createdAt,
            String referralCode
    ) {
        public static CustomerProfile from(Customer customer) {
            return new CustomerProfile(
                    customer.getId(), customer.getFirstName(), customer.getLastName(),
                    customer.getEmail(), customer.getPhone(), customer.getCompanyName(),
                    customer.getCustomerType(), customer.getLocale(), customer.isEmailVerified(),
                    customer.isMarketingOptIn(), customer.getCreatedAt(), customer.getReferralCode());
        }
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @Size(max = 40) String phone,
            @Size(max = 160) String companyName,
            @Size(max = 64) String taxNumber,
            Customer.Type customerType,
            @Size(max = 8) String locale,
            Boolean marketingOptIn
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 10, max = 128) String newPassword
    ) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 190) String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 10, max = 128) String password
    ) {
    }

    // --- addresses ----------------------------------------------------------

    public record AddressResponse(
            Long id,
            String title,
            String fullName,
            String phone,
            String country,
            String city,
            String district,
            String addressLine,
            String postalCode,
            String notes,
            boolean isDefault
    ) {
        public static AddressResponse from(Address address) {
            return new AddressResponse(
                    address.getId(), address.getTitle(), address.getFullName(), address.getPhone(),
                    address.getCountry(), address.getCity(), address.getDistrict(),
                    address.getAddressLine(), address.getPostalCode(), address.getNotes(),
                    address.isDefault());
        }
    }

    public record AddressRequest(
            @NotBlank @Size(max = 80) String title,
            @Size(max = 160) String fullName,
            @Size(max = 40) String phone,
            @Size(max = 80) String country,
            @NotBlank @Size(max = 120) String city,
            @Size(max = 120) String district,
            @NotBlank @Size(max = 1000) String addressLine,
            @Size(max = 24) String postalCode,
            @Size(max = 500) String notes,
            Boolean isDefault
    ) {
    }

    // --- admin view ---------------------------------------------------------

    public record CustomerRow(
            Long id,
            String firstName,
            String lastName,
            String email,
            String phone,
            String companyName,
            Customer.Status status,
            long orderCount,
            OffsetDateTime lastLoginAt,
            OffsetDateTime createdAt
    ) {
    }

    public record StatusRequest(Customer.Status status) {
    }
}
