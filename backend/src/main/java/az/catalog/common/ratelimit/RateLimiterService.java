package az.catalog.common.ratelimit;

import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Token-bucket limiter for the endpoints an anonymous caller can reach: login, password
 * reset, contact form, newsletter and chat.
 *
 * Buckets live in memory and expire an hour after their last use, which is the right
 * trade-off for a single-node deployment. Moving to Redis later means replacing the
 * backing {@link Cache} — the call sites do not change.
 */
@Service
public class RateLimiterService {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofHours(1))
            .build();

    /**
     * @param key      identity of the caller for this limit (usually {@code "login:" + ip})
     * @param capacity how many calls are allowed inside the window
     * @param window   length of the refill window
     * @throws ApiException {@link ErrorCode#RATE_LIMIT_EXCEEDED} when the bucket is empty
     */
    public void consume(String key, int capacity, Duration window) {
        Bucket bucket = buckets.get(key, ignored -> new Bucket(capacity, window.toNanos()));
        if (bucket != null && !bucket.tryConsume()) {
            throw new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
    }

    /** Clears the bucket, e.g. after a successful login so a legitimate user is not punished. */
    public void reset(String key) {
        buckets.invalidate(key);
    }

    /**
     * Classic token bucket with continuous refill: {@code capacity} tokens are restored
     * evenly over {@code refillNanos}, so a caller who waits half the window gets half
     * their allowance back rather than nothing until a hard reset.
     */
    private static final class Bucket {

        private final int capacity;
        private final long refillNanos;

        private double tokens;
        private long lastRefillNanos;

        Bucket(int capacity, long refillNanos) {
            this.capacity = capacity;
            this.refillNanos = refillNanos;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            long elapsed = now - lastRefillNanos;
            if (elapsed <= 0) {
                return;
            }
            double restored = (double) elapsed / refillNanos * capacity;
            tokens = Math.min(capacity, tokens + restored);
            lastRefillNanos = now;
        }
    }
}
