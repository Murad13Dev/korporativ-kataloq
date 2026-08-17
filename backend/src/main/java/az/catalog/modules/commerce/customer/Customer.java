package az.catalog.modules.commerce.customer;

import az.catalog.common.domain.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Storefront shopper. A separate identity domain from admin {@code User}: different
 * login endpoint, different token audience, no access to {@code /api/admin}.
 *
 * Guest checkout creates a row with {@code passwordHash == null} and status GUEST, so an
 * order always has a customer to hang off, and the shopper can later claim the account by
 * registering with the same address.
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer extends SoftDeletableEntity {

    public enum Status {
        ACTIVE, INACTIVE, BLOCKED, GUEST
    }

    public enum Type {
        INDIVIDUAL, COMPANY
    }

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "email", nullable = false, length = 190)
    private String email;

    @Column(name = "phone", length = 40)
    private String phone;

    /** {@code null} for guest records. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "company_name", length = 160)
    private String companyName;

    @Column(name = "tax_number", length = 64)
    private String taxNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 16)
    @Builder.Default
    private Type customerType = Type.INDIVIDUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(name = "locale", nullable = false, length = 8)
    @Builder.Default
    private String locale = "az";

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "marketing_opt_in", nullable = false)
    private boolean marketingOptIn;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    /** This customer's own shareable code — generated once, at registration. */
    @Column(name = "referral_code", length = 16)
    private String referralCode;

    /** Who brought them in, captured at registration; {@code null} for organic sign-ups. */
    @Column(name = "referred_by_customer_id")
    private Long referredByCustomerId;

    /** Set once the referrer's reward has been minted, so a customer's first order can
        never pay out twice (e.g. COMPLETED -> refunded-and-recompleted). */
    @Column(name = "referral_rewarded_at")
    private OffsetDateTime referralRewardedAt;

    public String fullName() {
        return firstName + " " + lastName;
    }

    public boolean canLogin() {
        return passwordHash != null && status == Status.ACTIVE && !isDeleted();
    }

    public boolean isGuest() {
        return status == Status.GUEST || passwordHash == null;
    }
}
