package az.catalog.modules.catalog.attribute;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AttributeGroupRepository extends JpaRepository<AttributeGroup, Long> {

    @EntityGraph(attributePaths = {"translations"})
    @Query("SELECT g FROM AttributeGroup g ORDER BY g.sortOrder, g.id")
    List<AttributeGroup> findAllOrdered();

    Optional<AttributeGroup> findByCode(String code);

    boolean existsByCode(String code);
}
