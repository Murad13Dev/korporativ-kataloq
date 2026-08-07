package az.catalog.modules.content;

import az.catalog.modules.content.ContentEntities.Statistic;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StatisticRepository extends JpaRepository<Statistic, Long> {

    @EntityGraph(attributePaths = {"translations"})
    @Query("""
            SELECT s FROM ContentEntities$Statistic s
            WHERE s.status = az.catalog.common.domain.ContentStatus.ACTIVE
            ORDER BY s.sortOrder, s.id
            """)
    List<Statistic> findActive();

    @EntityGraph(attributePaths = {"translations"})
    @Query("SELECT s FROM ContentEntities$Statistic s ORDER BY s.sortOrder, s.id")
    List<Statistic> findAllOrdered();
}
