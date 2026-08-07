package az.catalog.modules.commerce.company;

import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.modules.commerce.customer.Customer;
import az.catalog.modules.commerce.customer.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * B2B company accounts.
 *
 * A company starts {@link CompanyStatus#PENDING} and its negotiated
 * {@code discountPercentage} is inert until an admin flips it to
 * {@link CompanyStatus#ACTIVE} — see the checkout-time lookup in
 * {@code CheckoutService.corporateDiscountFor}.
 */
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository repository;
    private final CompanyMemberRepository memberRepository;
    private final CustomerRepository customerRepository;

    // =======================================================================
    // Customer-facing
    // =======================================================================

    @Transactional(readOnly = true)
    public Optional<CompanyDtos.CompanyView> myCompany(Long customerId) {
        return memberRepository.findByCustomerId(customerId).map(this::toView);
    }

    @Transactional
    public CompanyDtos.CompanyView create(Long ownerCustomerId, CompanyDtos.CreateCompanyRequest request) {
        if (memberRepository.existsByCustomerId(ownerCustomerId)) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "Siz artıq bir şirkətin üzvüsünüz");
        }
        Customer owner = customerRepository.findAliveById(ownerCustomerId)
                .orElseThrow(() -> ApiException.notFound("Müştəri", ownerCustomerId));

        Company company = repository.save(Company.builder()
                .name(request.name().trim())
                .taxNumber(blankToNull(request.taxNumber()))
                .build());

        memberRepository.save(CompanyMember.builder()
                .company(company)
                .customer(owner)
                .role(CompanyRole.OWNER)
                .build());

        // The registration flow only ever asks for these once; becoming a company owner
        // here is the other on-ramp, so the customer's own record is kept in sync too.
        owner.setCompanyName(company.getName());
        if (company.getTaxNumber() != null) {
            owner.setTaxNumber(company.getTaxNumber());
        }
        owner.setCustomerType(Customer.Type.COMPANY);

        return toView(requireMembership(ownerCustomerId));
    }

    @Transactional
    public CompanyDtos.MemberView inviteMember(Long ownerCustomerId, CompanyDtos.InviteMemberRequest request) {
        CompanyMember ownerMembership = requireMembership(ownerCustomerId);
        requireOwner(ownerMembership);

        String email = request.email().trim().toLowerCase();
        Customer target = customerRepository.findAliveByEmail(email)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Bu e-poçtla qeydiyyatlı istifadəçi tapılmadı — əvvəlcə hesab yaratmalıdır"));

        if (memberRepository.existsByCustomerId(target.getId())) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT,
                    "Bu istifadəçi artıq bir şirkətin üzvüdür");
        }

        CompanyMember member = memberRepository.save(CompanyMember.builder()
                .company(ownerMembership.getCompany())
                .customer(target)
                .role(CompanyRole.BUYER)
                .build());

        return toMemberView(member);
    }

    @Transactional
    public void removeMember(Long ownerCustomerId, Long memberId) {
        CompanyMember ownerMembership = requireMembership(ownerCustomerId);
        requireOwner(ownerMembership);

        CompanyMember target = memberRepository.findById(memberId)
                .orElseThrow(() -> ApiException.notFound("Üzv", memberId));

        if (!target.getCompany().getId().equals(ownerMembership.getCompany().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (target.getRole() == CompanyRole.OWNER) {
            throw ApiException.badRequest("Şirkət sahibi çıxarıla bilməz");
        }
        memberRepository.delete(target);
    }

    // =======================================================================
    // Admin
    // =======================================================================

    @Transactional(readOnly = true)
    public Page<CompanyDtos.CompanyRow> adminSearch(String search, Pageable pageable) {
        return repository.search(blankToNull(search), pageable).map(this::toRow);
    }

    @Transactional(readOnly = true)
    public CompanyDtos.CompanyView adminGet(Long companyId) {
        Company company = load(companyId);
        List<CompanyDtos.MemberView> members = memberRepository
                .findByCompanyIdOrderByRoleAscCreatedAtAsc(companyId).stream()
                .map(this::toMemberView)
                .toList();
        return new CompanyDtos.CompanyView(company.getId(), company.getName(), company.getTaxNumber(),
                company.getDiscountPercentage(), company.getStatus(), null, members);
    }

    @Transactional
    public void setDiscount(Long companyId, BigDecimal discountPercentage) {
        Company company = load(companyId);
        company.setDiscountPercentage(discountPercentage == null ? BigDecimal.ZERO : discountPercentage);
    }

    @Transactional
    public void setStatus(Long companyId, CompanyStatus status) {
        Company company = load(companyId);
        company.setStatus(status);
    }

    // =======================================================================
    // Internals
    // =======================================================================

    private Company load(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Şirkət", id));
    }

    private CompanyMember requireMembership(Long customerId) {
        return memberRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Siz heç bir şirkətin üzvü deyilsiniz"));
    }

    private void requireOwner(CompanyMember membership) {
        if (membership.getRole() != CompanyRole.OWNER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Yalnız şirkət sahibi bu əməliyyatı edə bilər");
        }
    }

    private CompanyDtos.CompanyView toView(CompanyMember membership) {
        Company company = membership.getCompany();
        List<CompanyDtos.MemberView> members = memberRepository
                .findByCompanyIdOrderByRoleAscCreatedAtAsc(company.getId()).stream()
                .map(this::toMemberView)
                .toList();
        return new CompanyDtos.CompanyView(company.getId(), company.getName(), company.getTaxNumber(),
                company.getDiscountPercentage(), company.getStatus(), membership.getRole(), members);
    }

    private CompanyDtos.MemberView toMemberView(CompanyMember member) {
        Customer customer = member.getCustomer();
        return new CompanyDtos.MemberView(member.getId(), customer.getId(), customer.fullName(),
                customer.getEmail(), member.getRole(), member.getCreatedAt());
    }

    private CompanyDtos.CompanyRow toRow(Company company) {
        return new CompanyDtos.CompanyRow(company.getId(), company.getName(), company.getTaxNumber(),
                company.getDiscountPercentage(), company.getStatus(),
                memberRepository.countByCompanyId(company.getId()), company.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
