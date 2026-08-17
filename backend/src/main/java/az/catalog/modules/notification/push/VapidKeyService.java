package az.catalog.modules.notification.push;

import az.catalog.common.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * VAPID identifies this server to push services (Chrome/Firefox/etc.) as the sender
 * every browser subscription trusts. {@code VAPID_PUBLIC_KEY}/{@code VAPID_PRIVATE_KEY}
 * win when set; otherwise a keypair is generated once on first boot and persisted to
 * {@link VapidKeys} — every restart after that reads the same row, so a container
 * restart never orphans a browser's existing subscription the way regenerating the
 * pair on every boot would.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VapidKeyService {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final VapidKeysRepository repository;
    private final AppProperties properties;

    @Getter
    private String publicKey;
    private String privateKey;

    @PostConstruct
    @Transactional
    public void init() {
        var configured = properties.getPush();
        if (configured.getPublicKey() != null && !configured.getPublicKey().isBlank()
                && configured.getPrivateKey() != null && !configured.getPrivateKey().isBlank()) {
            publicKey = configured.getPublicKey();
            privateKey = configured.getPrivateKey();
            log.info("VAPID keypair loaded from configuration");
            return;
        }

        VapidKeys stored = repository.findAll().stream().findFirst().orElse(null);
        if (stored != null) {
            publicKey = stored.getPublicKey();
            privateKey = stored.getPrivateKey();
            log.info("VAPID keypair loaded from database");
            return;
        }

        generateAndPersist();
    }

    private void generateAndPersist() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(Utils.ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            generator.initialize(new ECGenParameterSpec(Utils.CURVE));
            KeyPair keyPair = generator.generateKeyPair();

            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            publicKey = encoder.encodeToString(Utils.encode((ECPublicKey) keyPair.getPublic()));
            privateKey = encoder.encodeToString(Utils.encode((ECPrivateKey) keyPair.getPrivate()));

            repository.save(VapidKeys.builder()
                    .publicKey(publicKey)
                    .privateKey(privateKey)
                    .createdAt(OffsetDateTime.now())
                    .build());

            log.warn("""

                    ══════════════════════════════════════════════════════════════
                     VAPID AÇAR CÜTÜ YARADILDI VƏ VERİLƏNLƏR BAZASINDA SAXLANILDI
                     Push bildirişləri işləməyə hazırdır — əlavə iş tələb olunmur.
                     İstəsəniz, VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY dəyişənlərini
                     təyin edərək bu cütü öz açarlarınızla əvəz edə bilərsiniz.
                    ══════════════════════════════════════════════════════════════
                    """);
        } catch (Exception ex) {
            // Push is an enhancement, not a critical path — a boot-time keygen failure
            // must not stop the rest of the application from starting.
            log.error("Failed to generate VAPID keypair; push notifications are disabled", ex);
        }
    }

    public boolean isConfigured() {
        return publicKey != null && privateKey != null;
    }

    public String privateKey() {
        return privateKey;
    }
}
