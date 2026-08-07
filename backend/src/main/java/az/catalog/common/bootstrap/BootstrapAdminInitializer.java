package az.catalog.common.bootstrap;

import az.catalog.common.config.AppProperties;
import az.catalog.common.security.JwtService;
import az.catalog.modules.user.User;
import az.catalog.modules.user.UserRepository;
import az.catalog.modules.user.UserRole;
import az.catalog.modules.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first SUPER_ADMIN on an empty installation.
 *
 * This lives in code rather than in a Flyway migration on purpose: a password hash
 * committed to a migration would be identical in every client deployment of this
 * white-label platform. When ADMIN_PASSWORD is unset a strong one is generated and
 * printed once — it never appears again.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.countByDeletedAtIsNull() > 0) {
            return;
        }

        var config = properties.getSecurity().getBootstrapAdmin();
        boolean generated = config.getPassword() == null || config.getPassword().isBlank();
        String password = generated
                ? jwtService.generateOpaqueToken().replaceAll("[^A-Za-z0-9]", "").substring(0, 20)
                : config.getPassword();

        User admin = User.builder()
                .firstName(config.getFirstName())
                .lastName(config.getLastName())
                .email(config.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(password))
                .role(UserRole.SUPER_ADMIN)
                .status(UserStatus.ACTIVE)
                .locale("az")
                .build();

        userRepository.save(admin);

        if (generated) {
            log.warn("""

                    ══════════════════════════════════════════════════════════════
                     İLK SUPER_ADMIN HESABI YARADILDI
                       E-poçt : {}
                       Şifrə  : {}
                     Bu şifrə yalnız bir dəfə göstərilir. Dərhal dəyişin.
                     Növbəti quraşdırmalarda ADMIN_PASSWORD dəyişənini təyin edin.
                    ══════════════════════════════════════════════════════════════
                    """, admin.getEmail(), password);
        } else {
            log.info("Bootstrap SUPER_ADMIN created for {} using the configured password", admin.getEmail());
        }
    }
}
