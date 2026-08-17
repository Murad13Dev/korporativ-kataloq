package az.catalog.modules.notification.push;

import az.catalog.common.exception.ApiException;
import az.catalog.modules.commerce.customer.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionRepository repository;
    private final CustomerRepository customerRepository;

    /** Upsert on endpoint: re-subscribing the same device (a renewed browser
        subscription, a re-login) replaces whichever customer owned it before. */
    @Transactional
    public void subscribe(Long customerId, String endpoint, String p256dh, String auth) {
        PushSubscription subscription = repository.findByEndpoint(endpoint)
                .orElseGet(() -> PushSubscription.builder()
                        .customer(customerRepository.getReferenceById(customerId))
                        .endpoint(endpoint)
                        .createdAt(OffsetDateTime.now())
                        .build());

        subscription.setCustomer(customerRepository.findAliveById(customerId)
                .orElseThrow(() -> ApiException.notFound("Müştəri", customerId)));
        subscription.setP256dhKey(p256dh);
        subscription.setAuthKey(auth);
        repository.save(subscription);
    }

    /** Idempotent: unsubscribing an endpoint that isn't registered is a no-op. */
    @Transactional
    public void unsubscribe(String endpoint) {
        repository.deleteByEndpoint(endpoint);
    }
}
