package az.catalog.modules.content;

import az.catalog.modules.content.ContentEntities.Testimonial;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TestimonialRepository extends JpaRepository<Testimonial, Long> {

    @EntityGraph(attributePaths = {"translations"})
    @Query("""
            SELECT t FROM ContentEntities$Testimonial t
            WHERE t.status = az.catalog.common.domain.ContentStatus.ACTIVE
            ORDER BY t.featured DESC, t.sortOrder, t.id
            """)
    List<Testimonial> findActive(Pageable pageable);

    @EntityGraph(attributePaths = {"translations"})
    @Query("SELECT t FROM ContentEntities$Testimonial t ORDER BY t.sortOrder, t.id")
    List<Testimonial> findAllOrdered();
}
