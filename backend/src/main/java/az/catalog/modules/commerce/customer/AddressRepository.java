package az.catalog.modules.commerce.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findAllByCustomerIdOrderByIsDefaultDescIdAsc(Long customerId);

    Optional<Address> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Address> findByCustomerIdAndIsDefaultTrue(Long customerId);

    long countByCustomerId(Long customerId);

    /** Clears the flag before setting a new default; the partial unique index allows one. */
    @Modifying
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.customer.id = :customerId AND a.isDefault = true")
    int clearDefault(@Param("customerId") Long customerId);
}
