package az.catalog.modules.catalog.attribute;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AttributeOptionRepository extends JpaRepository<AttributeOption, Long> {

    @EntityGraph(attributePaths = {"translations", "attribute"})
    @Query("SELECT o FROM AttributeOption o WHERE o.id IN :ids")
    List<AttributeOption> findAllDetailByIds(@Param("ids") Collection<Long> ids);

    List<AttributeOption> findAllByAttributeIdOrderBySortOrderAsc(Long attributeId);

    @Query("SELECT COUNT(v) FROM ProductAttributeValue v WHERE v.option.id = :optionId")
    long countUsages(@Param("optionId") Long optionId);

    /** Option ids that at least one published product actually carries — powers facet counts. */
    @Query(value = """
            SELECT v.option_id, COUNT(DISTINCT v.product_id)
            FROM product_attribute_values v
            JOIN products p ON p.id = v.product_id
            WHERE v.option_id IS NOT NULL
              AND p.deleted_at IS NULL AND p.status = 'ACTIVE'
            GROUP BY v.option_id
            """, nativeQuery = true)
    List<Object[]> countProductsPerOption();
}
