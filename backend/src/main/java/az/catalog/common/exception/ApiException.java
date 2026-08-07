package az.catalog.common.exception;

import lombok.Getter;

import java.util.Map;

/**
 * Single checked-at-the-edge exception type. Services throw it with an {@link ErrorCode};
 * {@link GlobalExceptionHandler} renders it into the documented error envelope.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, String> details;

    public ApiException(ErrorCode code) {
        this(code, code.getDefaultMessage(), null);
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ApiException(ErrorCode code, String message, Map<String, String> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    // --- shorthand factories used across the modules -------------------------

    public static ApiException notFound(String resource, Object id) {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                "%s tapılmadı (id: %s)".formatted(resource, id));
    }

    public static ApiException notFoundBySlug(String resource, String slug) {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                "%s tapılmadı (slug: %s)".formatted(resource, slug));
    }

    public static ApiException conflict(ErrorCode code) {
        return new ApiException(code);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message);
    }

    public static ApiException field(ErrorCode code, String field, String message) {
        return new ApiException(code, code.getDefaultMessage(), Map.of(field, message));
    }
}
