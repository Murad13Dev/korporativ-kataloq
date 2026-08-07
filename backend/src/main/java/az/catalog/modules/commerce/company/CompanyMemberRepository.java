package az.catalog.modules.commerce.company;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyMemberRepository extends JpaRepository<CompanyMember, Long> {

    @EntityGraph(attributePaths = {"company"})
    Optional<CompanyMember> findByCustomerId(Long customerId);

    @EntityGraph(attributePaths = {"customer"})
    List<CompanyMember> findByCompanyIdOrderByRoleAscCreatedAtAsc(Long companyId);

    long countByCompanyId(Long companyId);

    boolean existsByCustomerId(Long customerId);
}
