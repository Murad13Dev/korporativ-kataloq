package az.catalog.modules.content;

import az.catalog.modules.content.ContentEntities.Benefit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BenefitRepository extends JpaRepository<Benefit, Long> {

    @EntityGraph(attributePaths = {"translations"})
    @Query("""
            SELECT b FROM ContentEntities$Benefit b
            WHERE b.status = az.catalog.common.domain.ContentStatus.ACTIVE
            ORDER BY b.sortOrder, b.id
            """)
    List<Benefit> findActive();

    @EntityGraph(attributePaths = {"translations"})
    @Query("SELECT b FROM ContentEntities$Benefit b ORDER BY b.sortOrder, b.id")
    List<Benefit> findAllOrdered();
}
