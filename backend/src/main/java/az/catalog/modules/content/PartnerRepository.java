package az.catalog.modules.content;

import az.catalog.modules.content.ContentEntities.Partner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PartnerRepository extends JpaRepository<Partner, Long> {

    @Query("""
            SELECT p FROM ContentEntities$Partner p
            WHERE p.status = az.catalog.common.domain.ContentStatus.ACTIVE
            ORDER BY p.sortOrder, p.id
            """)
    List<Partner> findActive();

    @Query("SELECT p FROM ContentEntities$Partner p ORDER BY p.sortOrder, p.id")
    List<Partner> findAllOrdered();
}
