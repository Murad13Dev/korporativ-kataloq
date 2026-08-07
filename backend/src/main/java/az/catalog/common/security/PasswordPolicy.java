package az.catalog.common.security;

import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Length-first password rules, in line with current NIST guidance: a long passphrase
 * beats a short string full of symbols, so the minimum is 10 characters and the only
 * composition requirement is that the password is not one of the obvious ones.
 */
@Component
public class PasswordPolicy {

    private static final int MIN_LENGTH = 10;
    private static final int MAX_LENGTH = 128;

    private static final Set<String> BANNED = Set.of(
            "password", "parol123", "12345678", "123456789", "1234567890",
            "qwertyuiop", "administrator", "admin12345", "welcome123", "passw0rd",
            "iloveyou", "letmein123", "azerbaycan");

    public void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new ApiException(ErrorCode.PASSWORD_TOO_WEAK,
                    "Şifrə ən azı %d simvoldan ibarət olmalıdır".formatted(MIN_LENGTH));
        }
        if (password.length() > MAX_LENGTH) {
            throw new ApiException(ErrorCode.PASSWORD_TOO_WEAK,
                    "Şifrə %d simvoldan uzun ola bilməz".formatted(MAX_LENGTH));
        }
        String normalized = password.toLowerCase().trim();
        if (BANNED.contains(normalized)) {
            throw new ApiException(ErrorCode.PASSWORD_TOO_WEAK,
                    "Bu şifrə çox geniş istifadə olunur. Başqa şifrə seçin");
        }
        if (normalized.chars().distinct().count() < 4) {
            throw new ApiException(ErrorCode.PASSWORD_TOO_WEAK,
                    "Şifrə daha müxtəlif simvollardan ibarət olmalıdır");
        }
    }
}
