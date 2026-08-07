package az.catalog.modules.commerce.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class CompanyDtos {

    private CompanyDtos() {
    }

    public record CreateCompanyRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 64) String taxNumber
    ) {
    }

    public record InviteMemberRequest(
            @NotBlank @Email @Size(max = 190) String email
    ) {
    }

    public record MemberView(
            Long id,
            Long customerId,
            String name,
            String email,
            CompanyRole role,
            OffsetDateTime createdAt
    ) {
    }

    /** What a customer sees for their own company. */
    public record CompanyView(
            Long id,
            String name,
            String taxNumber,
            BigDecimal discountPercentage,
            CompanyStatus status,
            CompanyRole myRole,
            List<MemberView> members
    ) {
    }

    /** Admin list row — no member detail, just the count. */
    public record CompanyRow(
            Long id,
            String name,
            String taxNumber,
            BigDecimal discountPercentage,
            CompanyStatus status,
            long memberCount,
            OffsetDateTime createdAt
    ) {
    }

    public record SetDiscountRequest(
            @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100")
            BigDecimal discountPercentage
    ) {
    }

    public record SetStatusRequest(
            CompanyStatus status
    ) {
    }
}
