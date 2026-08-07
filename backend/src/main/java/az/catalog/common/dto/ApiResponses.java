package az.catalog.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.domain.Page;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The three response shapes the whole API speaks, exactly as fixed in the API contract:
 * a single-object envelope, a page envelope and an error envelope.
 */
public final class ApiResponses {

    private ApiResponses() {
    }

    /** {@code { "data": { ... } }} */
    public record DataResponse<T>(T data) {
        public static <T> DataResponse<T> of(T data) {
            return new DataResponse<>(data);
        }
    }

    /** {@code { "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }} */
    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last
    ) {
        public static <T> PageResponse<T> of(Page<T> page) {
            return new PageResponse<>(
                    page.getContent(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages(),
                    page.isFirst(),
                    page.isLast()
            );
        }

        public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
            return new PageResponse<>(
                    page.getContent().stream().map(mapper).toList(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages(),
                    page.isFirst(),
                    page.isLast()
            );
        }

        public static <T> PageResponse<T> of(List<T> content) {
            return new PageResponse<>(content, 0, content.size(), content.size(), 1, true, true);
        }
    }

    /**
     * {@code { "timestamp", "status", "code", "message", "errors": { "field": "message" } }}
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(
            OffsetDateTime timestamp,
            int status,
            String code,
            String message,
            String path,
            Map<String, String> errors
    ) {
    }
}
