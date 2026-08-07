package az.catalog.common.exception;

import az.catalog.common.dto.ApiResponses.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates every exception into the documented error envelope. Nothing internal
 * (stack traces, SQL, constraint names) is ever echoed back to the client.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        if (ex.getCode().getStatus().is5xxServerError()) {
            log.error("API error {} on {}", ex.getCode(), request.getRequestURI(), ex);
        } else {
            log.debug("API error {} on {}: {}", ex.getCode(), request.getRequestURI(), ex.getMessage());
        }
        return build(ex.getCode(), ex.getMessage(), request, ex.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
            errors.putIfAbsent(field, error.getDefaultMessage());
        });
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDefaultMessage(), request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v ->
                errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDefaultMessage(), request, errors);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        log.debug("Malformed request on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.VALIDATION_ERROR, "Sorğu formatı düzgün deyil", request, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex,
                                                              HttpServletRequest request) {
        return build(ErrorCode.PAYLOAD_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE.getDefaultMessage(), request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.getDefaultMessage(), request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(ErrorCode.UNAUTHENTICATED, ErrorCode.UNAUTHENTICATED.getDefaultMessage(), request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex,
                                                         HttpServletRequest request) {
        // The concrete constraint name is intentionally kept out of the response body.
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.RESOURCE_CONFLICT, ErrorCode.RESOURCE_CONFLICT.getDefaultMessage(), request, null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                              HttpServletRequest request) {
        return build(ErrorCode.RESOURCE_CONFLICT,
                "Məlumat başqa istifadəçi tərəfindən dəyişdirilib. Səhifəni yeniləyin", request, null);
    }

    @ExceptionHandler({NoHandlerFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ErrorResponse> handleNoHandler(Exception ex, HttpServletRequest request) {
        return build(ErrorCode.RESOURCE_NOT_FOUND, "Endpoint tapılmadı", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage(), request, null);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message,
                                                HttpServletRequest request, Map<String, String> errors) {
        HttpStatus status = code.getStatus();
        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now(),
                status.value(),
                code.name(),
                message != null ? message : code.getDefaultMessage(),
                request != null ? request.getRequestURI() : null,
                errors == null || errors.isEmpty() ? null : new HashMap<>(errors)
        );
        return ResponseEntity.status(status).body(body);
    }
}
