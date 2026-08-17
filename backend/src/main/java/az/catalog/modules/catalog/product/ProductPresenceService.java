package az.catalog.modules.catalog.product;

import az.catalog.common.config.WebSocketConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Live "N people viewing this product" presence.
 *
 * In-memory only, by design: a viewer count reset on deploy or lost across a multi-node
 * restart is a cosmetic blip, not data loss, so this carries none of the durability (or
 * write load) a database table would cost for something that is stale within seconds
 * anyway. The visitor token is the same one the chat widget already generates and
 * stores — reusing it means no second identity concept for the frontend to carry.
 *
 * A heartbeat refreshes the visitor's timestamp and immediately broadcasts the new
 * count; a periodic sweep drops anyone not seen within {@link #TIMEOUT}, which is how a
 * closed tab is detected without needing a reliable "I'm leaving" signal on unload.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductPresenceService {

    private static final Duration TIMEOUT = Duration.ofSeconds(45);

    private final SimpMessagingTemplate messaging;

    /** productId -> (visitorToken -> last heartbeat). */
    private final Map<Long, Map<String, Instant>> viewersByProduct = new ConcurrentHashMap<>();

    public int heartbeat(Long productId, String visitorToken) {
        Map<String, Instant> viewers = viewersByProduct.computeIfAbsent(productId, id -> new ConcurrentHashMap<>());
        viewers.put(visitorToken, Instant.now());
        int count = viewers.size();
        broadcast(productId, count);
        return count;
    }

    /** Runs independently of any single request, so an idle product's count decays even
        if nobody ever loads that page again to trigger the drop. */
    @Scheduled(fixedDelay = 20_000)
    public void sweep() {
        Instant cutoff = Instant.now().minus(TIMEOUT);
        viewersByProduct.forEach((productId, viewers) -> {
            boolean changed = viewers.values().removeIf(lastSeen -> lastSeen.isBefore(cutoff));
            if (changed) {
                broadcast(productId, viewers.size());
            }
        });
    }

    /**
     * A point-in-time read for the admin dashboard's live-visitor tile — viewer counts
     * per product, highest first. Deliberately not paginated or persisted: it's a snapshot
     * of an in-memory map, gone the moment nobody is browsing.
     */
    public Map<Long, Integer> snapshot() {
        return viewersByProduct.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().size(),
                        (a, b) -> a, LinkedHashMap::new));
    }

    private void broadcast(Long productId, int count) {
        try {
            messaging.convertAndSend(
                    WebSocketConfig.TOPIC_PRODUCT_PRESENCE + productId, Map.of("count", count));
        } catch (RuntimeException ex) {
            // Presence is a nice-to-have; a broker hiccup must not fail the heartbeat call.
            log.debug("Presence broadcast failed for product {}: {}", productId, ex.getMessage());
        }
    }
}
