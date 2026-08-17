package az.catalog.modules.notification.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Exactly one row, ever. See {@link VapidKeyService} for why this is a table and not
 * just a generated-at-startup value: unlike the bootstrap admin password (a one-time
 * credential nobody needs to read back), a VAPID keypair must stay identical across
 * restarts, or every browser subscription created against the old key silently stops
 * being deliverable.
 */
@Entity
@Table(name = "vapid_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VapidKeys {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_key", nullable = false)
    private String publicKey;

    @Column(name = "private_key", nullable = false)
    private String privateKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
