package az.catalog.modules.siteconfig;

import az.catalog.common.domain.BaseEntity;
import az.catalog.common.domain.VersionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;

/**
 * One immutable snapshot of the whole site configuration.
 *
 * Publishing never mutates a live row: a new DRAFT is created, edited, then flipped to
 * PUBLISHED while the previous PUBLISHED becomes ARCHIVED. That makes rollback a matter
 * of copying an old document into a fresh draft.
 */
@Entity
@Table(name = "site_configuration_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiteConfigurationVersion extends BaseEntity {

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private VersionStatus status;

    @Column(name = "label", length = 160)
    private String label;

    @Type(JsonBinaryType.class)
    @Column(name = "configuration", nullable = false, columnDefinition = "jsonb")
    private JsonNode configuration;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "published_by")
    private Long publishedBy;
}
