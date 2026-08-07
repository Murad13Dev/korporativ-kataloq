package az.catalog.modules.catalog.attribute;

import az.catalog.common.domain.ContentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttributeRepository extends JpaRepository<Attribute, Long> {

    /**
     * Only {@code options} and {@code group} are join-fetched — both {@code translations}
     * (the attribute's own) and {@code options.translations} lazy-load instead. {@code options}
     * is a {@code List} (bag); joining ANY other collection alongside it, even one nested a
     * level deeper like {@code options.translations}, fans the SQL result out to one row per
     * combination — an attribute with 2 languages and 3 options would otherwise come back
     * with each option listed twice. {@code SELECT DISTINCT} does not reliably fix this for
     * {@code @EntityGraph}-driven fetches in this Hibernate version (confirmed the hard way
     * on the cart's line items this session) — the join is simply not created instead. The
     * lazy collections batch efficiently via {@code default_batch_fetch_size} regardless.
     */
    @EntityGraph(attributePaths = {"options", "group"})
    @Query("SELECT a FROM Attribute a WHERE a.id = :id")
    Optional<Attribute> findDetailById(@Param("id") Long id);

    Optional<Attribute> findByCode(String code);

    boolean existsByCode(String code);

    @EntityGraph(attributePaths = {"translations", "group"})
    @Query("SELECT a FROM Attribute a WHERE (:status IS NULL OR a.status = :status) ORDER BY a.sortOrder")
    List<Attribute> findAllOrdered(@Param("status") ContentStatus status);

    @EntityGraph(attributePaths = {"options", "group"})
    @Query("""
            SELECT a FROM Attribute a
            WHERE a.filterable = true AND a.status = az.catalog.common.domain.ContentStatus.ACTIVE
            ORDER BY a.sortOrder
            """)
    List<Attribute> findFilterable();

    @EntityGraph(attributePaths = {"options", "group"})
    @Query("SELECT a FROM Attribute a WHERE a.id IN :ids")
    List<Attribute> findAllDetailByIds(@Param("ids") Collection<Long> ids);

    @Query("SELECT COUNT(v) FROM ProductAttributeValue v WHERE v.attribute.id = :attributeId")
    long countUsages(@Param("attributeId") Long attributeId);
}
