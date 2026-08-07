package az.catalog.modules.language;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LanguageRepository extends JpaRepository<Language, String> {

    List<Language> findAllByIsActiveTrueOrderBySortOrderAsc();

    List<Language> findAllByOrderBySortOrderAsc();

    Optional<Language> findByIsDefaultTrue();

    boolean existsByCodeAndIsActiveTrue(String code);

    long countByIsActiveTrue();
}
