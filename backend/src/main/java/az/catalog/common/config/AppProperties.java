package az.catalog.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Typed view over the {@code app.*} configuration tree. Everything that can differ between
 * deployments of this white-label platform is funnelled through here so a new client
 * installation only needs environment variables — never a code change.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String publicUrl = "http://localhost:5173";
    private String apiUrl = "http://localhost:8080";

    private Cors cors = new Cors();
    private Security security = new Security();
    private Storage storage = new Storage();
    private Mail mail = new Mail();
    private Commerce commerce = new Commerce();
    private Payment payment = new Payment();
    private Chat chat = new Chat();

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173");
    }

    @Getter
    @Setter
    public static class Security {
        private Jwt jwt = new Jwt();
        private RefreshCookie refreshCookie = new RefreshCookie();
        private BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();
        private RateLimit rateLimit = new RateLimit();

        @Getter
        @Setter
        public static class Jwt {
            private String secret;
            private Duration accessTokenTtl = Duration.ofMinutes(30);
            private Duration refreshTokenTtl = Duration.ofDays(14);
            private String issuer = "catalog-platform";
        }

        @Getter
        @Setter
        public static class RefreshCookie {
            private String name = "ctl_rt";
            private String path = "/api";
            private boolean secure = false;
            private String sameSite = "Lax";
        }

        @Getter
        @Setter
        public static class BootstrapAdmin {
            private String email = "admin@company.az";
            /** Left blank on purpose: a generated password is logged once on first boot. */
            private String password = "";
            private String firstName = "Super";
            private String lastName = "Admin";
        }

        @Getter
        @Setter
        public static class RateLimit {
            private int loginAttemptsPerMinute = 5;
            private int publicFormSubmissionsPerHour = 10;
        }
    }

    @Getter
    @Setter
    public static class Storage {
        private String type = "local";
        private Local local = new Local();
        private long maxImageSizeBytes = 10L * 1024 * 1024;
        private long maxDocumentSizeBytes = 20L * 1024 * 1024;
        private long maxVideoSizeBytes = 20L * 1024 * 1024;
        private List<String> allowedImageTypes = List.of("image/jpeg", "image/png", "image/webp");
        private List<String> allowedDocumentTypes = List.of("application/pdf");
        private List<String> allowedVideoTypes = List.of("video/mp4", "video/webm");

        @Getter
        @Setter
        public static class Local {
            private String root = "./data/uploads";
            private String publicBaseUrl = "http://localhost:8080/uploads";
        }
    }

    @Getter
    @Setter
    public static class Mail {
        private String from = "noreply@company.az";
        private String adminNotificationTo = "admin@company.az";
        private boolean enabled = false;
    }

    @Getter
    @Setter
    public static class Commerce {
        private boolean enabled = true;
        private String currency = "AZN";
        private BigDecimal freeDeliveryThreshold = new BigDecimal("150");
        private BigDecimal defaultDeliveryPrice = new BigDecimal("5");
        private boolean guestCheckoutEnabled = true;
        private int paymentWindowMinutes = 30;
    }

    @Getter
    @Setter
    public static class Payment {
        private String provider = "mock";
        private String returnUrl = "http://localhost:5173/checkout/result";
        private String webhookSecret = "";
        private Stripe stripe = new Stripe();

        @Getter
        @Setter
        public static class Stripe {
            private String secretKey = "";
            private String publishableKey = "";
        }
    }

    @Getter
    @Setter
    public static class Chat {
        private boolean enabled = true;
        private int autoCloseAfterHours = 72;
    }
}
