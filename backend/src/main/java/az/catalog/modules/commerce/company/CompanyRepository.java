package az.catalog.modules.commerce.company;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    @Query("""
            SELECT c FROM Company c
            WHERE :search IS NULL
               OR lower(c.name) LIKE lower(concat('%', :search, '%'))
               OR lower(c.taxNumber) LIKE lower(concat('%', :search, '%'))
            """)
    Page<Company> search(@Param("search") String search, Pageable pageable);
}
