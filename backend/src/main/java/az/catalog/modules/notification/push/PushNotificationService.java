package az.catalog.modules.notification.push;

import az.catalog.common.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Order-status browser notifications — the push equivalent of {@code MailService}'s
 * {@code sendOrderStatusChanged}, sent alongside it rather than instead of it. Every
 * send is best-effort: a delivery failure must never touch the order itself, and an
 * expired subscription (push service answers 404/410) is quietly cleaned up rather
 * than retried.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final PushSubscriptionRepository repository;
    private final VapidKeyService vapidKeyService;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    private volatile PushService pushService;

    @Async
    public void sendToCustomer(Long customerId, String title, String body, String url) {
        if (!vapidKeyService.isConfigured()) {
            return;
        }
        List<PushSubscription> subscriptions = repository.findByCustomerId(customerId);
        if (subscriptions.isEmpty()) {
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of("title", title, "body", body, "url", url));
        } catch (Exception ex) {
            log.error("Failed to serialise push payload", ex);
            return;
        }

        for (PushSubscription subscription : subscriptions) {
            sendOne(subscription, payload);
        }
    }

    private void sendOne(PushSubscription subscription, String payload) {
        try {
            Subscription target = new Subscription(
                    subscription.getEndpoint(),
                    new Subscription.Keys(subscription.getP256dhKey(), subscription.getAuthKey()));

            HttpResponse response = pushService().send(new Notification(target, payload));
            int status = response.getStatusLine().getStatusCode();

            // 404/410: the push service itself says this registration is gone — the
            // browser unsubscribed, the profile was reset, etc. Anything else (network
            // hiccup, momentary 5xx) is logged and left alone rather than deleted, since
            // it may well succeed on the next order-status change.
            if (status == 404 || status == 410) {
                repository.deleteByEndpoint(subscription.getEndpoint());
            } else if (status >= 300) {
                log.warn("Push delivery to {} failed with status {}", subscription.getEndpoint(), status);
            }
        } catch (Exception ex) {
            log.warn("Push delivery to {} failed: {}", subscription.getEndpoint(), ex.getMessage());
        }
    }

    private PushService pushService() throws Exception {
        PushService service = pushService;
        if (service == null) {
            synchronized (this) {
                service = pushService;
                if (service == null) {
                    String subject = "mailto:" + properties.getMail().getFrom();
                    service = new PushService(vapidKeyService.getPublicKey(), vapidKeyService.privateKey(), subject);
                    pushService = service;
                }
            }
        }
        return service;
    }
}
