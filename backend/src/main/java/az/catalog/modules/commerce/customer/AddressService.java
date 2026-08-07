package az.catalog.modules.commerce.customer;

import az.catalog.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {

    private static final int MAX_ADDRESSES = 20;

    private final AddressRepository addressRepository;
    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public List<CustomerDtos.AddressResponse> list(Long customerId) {
        return addressRepository.findAllByCustomerIdOrderByIsDefaultDescIdAsc(customerId).stream()
                .map(CustomerDtos.AddressResponse::from)
                .toList();
    }

    @Transactional
    public CustomerDtos.AddressResponse create(Long customerId, CustomerDtos.AddressRequest request) {
        if (addressRepository.countByCustomerId(customerId) >= MAX_ADDRESSES) {
            throw ApiException.badRequest("Maksimum %d ünvan saxlaya bilərsiniz".formatted(MAX_ADDRESSES));
        }
        Customer customer = customerRepository.findAliveById(customerId)
                .orElseThrow(() -> ApiException.notFound("Müştəri", customerId));

        // The first address is the default by definition; there is nothing to compare it to.
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault())
                || addressRepository.countByCustomerId(customerId) == 0;
        if (makeDefault) {
            addressRepository.clearDefault(customerId);
            addressRepository.flush();
        }

        Address address = addressRepository.save(Address.builder()
                .customer(customer)
                .title(request.title().trim())
                .fullName(request.fullName())
                .phone(request.phone())
                .country(blankOr(request.country(), "Azərbaycan"))
                .city(request.city().trim())
                .district(request.district())
                .addressLine(request.addressLine().trim())
                .postalCode(request.postalCode())
                .notes(request.notes())
                .isDefault(makeDefault)
                .build());

        return CustomerDtos.AddressResponse.from(address);
    }

    @Transactional
    public CustomerDtos.AddressResponse update(Long customerId, Long addressId,
                                               CustomerDtos.AddressRequest request) {
        Address address = load(customerId, addressId);

        if (Boolean.TRUE.equals(request.isDefault()) && !address.isDefault()) {
            addressRepository.clearDefault(customerId);
            addressRepository.flush();
            address.setDefault(true);
        }

        address.setTitle(request.title().trim());
        address.setFullName(request.fullName());
        address.setPhone(request.phone());
        address.setCountry(blankOr(request.country(), "Azərbaycan"));
        address.setCity(request.city().trim());
        address.setDistrict(request.district());
        address.setAddressLine(request.addressLine().trim());
        address.setPostalCode(request.postalCode());
        address.setNotes(request.notes());

        return CustomerDtos.AddressResponse.from(address);
    }

    @Transactional
    public CustomerDtos.AddressResponse makeDefault(Long customerId, Long addressId) {
        Address address = load(customerId, addressId);
        addressRepository.clearDefault(customerId);
        addressRepository.flush();
        address.setDefault(true);
        return CustomerDtos.AddressResponse.from(address);
    }

    @Transactional
    public void delete(Long customerId, Long addressId) {
        Address address = load(customerId, addressId);
        boolean wasDefault = address.isDefault();
        addressRepository.delete(address);

        // Deleting the default promotes the next address so the account always has one.
        if (wasDefault) {
            addressRepository.flush();
            addressRepository.findAllByCustomerIdOrderByIsDefaultDescIdAsc(customerId).stream()
                    .findFirst()
                    .ifPresent(next -> next.setDefault(true));
        }
    }

    private Address load(Long customerId, Long addressId) {
        return addressRepository.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> ApiException.notFound("Ünvan", addressId));
    }

    private static String blankOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
