package az.catalog.modules.commerce.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    boolean existsByProviderAndExternalEventId(String provider, String externalEventId);

    List<PaymentEvent> findAllByPaymentIdOrderByCreatedAtDesc(Long paymentId);
}
