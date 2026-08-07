package az.catalog.modules.user;

import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.common.security.JwtService;
import az.catalog.common.security.PasswordPolicy;
import az.catalog.modules.auth.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Admin account management. Reachable only by SUPER_ADMIN (enforced in SecurityConfig).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public Page<UserDtos.UserResponse> search(String search, UserRole role, UserStatus status, Pageable pageable) {
        return userRepository.search(emptyToNull(search), role, status, pageable)
                .map(UserDtos.UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserDtos.UserResponse get(Long id) {
        return UserDtos.UserResponse.from(load(id));
    }

    @Transactional
    public UserDtos.UserResponse create(UserDtos.CreateRequest request) {
        if (userRepository.existsByEmail(request.email(), null)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        String password = request.password() != null && !request.password().isBlank()
                ? request.password()
                : jwtService.generateOpaqueToken().substring(0, 16);
        passwordPolicy.validate(password);

        User user = User.builder()
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(request.email().trim().toLowerCase())
                .phone(request.phone())
                .role(request.role())
                .status(UserStatus.ACTIVE)
                .locale(request.locale() == null || request.locale().isBlank() ? "az" : request.locale())
                .passwordHash(passwordEncoder.encode(password))
                .build();

        User saved = userRepository.save(user);
        if (request.password() == null || request.password().isBlank()) {
            // Surfaced once in the logs; the operator is expected to hand it over securely
            // and the new admin changes it on first login.
            log.info("Created admin {} with generated password: {}", saved.getEmail(), password);
        }
        return UserDtos.UserResponse.from(saved);
    }

    @Transactional
    public UserDtos.UserResponse update(Long id, UserDtos.UpdateRequest request) {
        User user = load(id);
        if (userRepository.existsByEmail(request.email(), id)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPhone(request.phone());
        user.setAvatarMediaId(request.avatarMediaId());
        if (request.locale() != null && !request.locale().isBlank()) {
            user.setLocale(request.locale());
        }
        return UserDtos.UserResponse.from(user);
    }

    @Transactional
    public UserDtos.UserResponse changeStatus(Long id, UserStatus status, Long actingUserId) {
        User user = load(id);
        guardSelf(id, actingUserId);
        if (status != UserStatus.ACTIVE) {
            guardLastSuperAdmin(user);
            // A deactivated admin must not keep a live session.
            refreshTokenRepository.revokeAllForUser(id, OffsetDateTime.now());
        }
        user.setStatus(status);
        return UserDtos.UserResponse.from(user);
    }

    @Transactional
    public UserDtos.UserResponse changeRole(Long id, UserRole role, Long actingUserId) {
        User user = load(id);
        guardSelf(id, actingUserId);
        if (user.getRole() == UserRole.SUPER_ADMIN && role != UserRole.SUPER_ADMIN) {
            guardLastSuperAdmin(user);
        }
        user.setRole(role);
        // Authority changes must not wait for the old access token to expire.
        refreshTokenRepository.revokeAllForUser(id, OffsetDateTime.now());
        return UserDtos.UserResponse.from(user);
    }

    @Transactional
    public void setPassword(Long id, String password) {
        User user = load(id);
        passwordPolicy.validate(password);
        user.setPasswordHash(passwordEncoder.encode(password));
        refreshTokenRepository.revokeAllForUser(id, OffsetDateTime.now());
        log.info("Password reset by super admin for user {}", id);
    }

    @Transactional
    public void delete(Long id, Long actingUserId) {
        User user = load(id);
        guardSelf(id, actingUserId);
        guardLastSuperAdmin(user);
        user.softDelete();
        refreshTokenRepository.revokeAllForUser(id, OffsetDateTime.now());
    }

    private void guardSelf(Long targetId, Long actingUserId) {
        if (targetId.equals(actingUserId)) {
            throw new ApiException(ErrorCode.CANNOT_MODIFY_SELF);
        }
    }

    private void guardLastSuperAdmin(User user) {
        if (user.getRole() == UserRole.SUPER_ADMIN
                && userRepository.countActiveSuperAdmins(user.getId()) == 0) {
            throw new ApiException(ErrorCode.LAST_SUPER_ADMIN);
        }
    }

    private User load(Long id) {
        return userRepository.findAliveById(id)
                .orElseThrow(() -> ApiException.notFound("İstifadəçi", id));
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
