package az.catalog.modules.siteconfig;

import az.catalog.common.domain.VersionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SiteConfigurationRepository extends JpaRepository<SiteConfigurationVersion, Long> {

    Optional<SiteConfigurationVersion> findFirstByStatus(VersionStatus status);

    Page<SiteConfigurationVersion> findAllByOrderByVersionNumberDesc(Pageable pageable);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM SiteConfigurationVersion v")
    int maxVersionNumber();
}
