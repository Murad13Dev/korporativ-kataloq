package az.catalog.modules.commerce.quote;

import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.dto.ApiResponses.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin · Təklif sorğuları")
@RestController
@RequestMapping("/api/admin/quote-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','SUPPORT')")
public class AdminQuoteController {

    private final QuoteRequestService service;

    @Operation(summary = "Sorğular siyahısı")
    @GetMapping
    public PageResponse<QuoteDtos.QuoteRow> search(
            @RequestParam(required = false) QuoteStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.adminSearch(status, search, pageable));
    }

    @Operation(summary = "Sorğu detalı")
    @GetMapping("/{id}")
    public DataResponse<QuoteDtos.QuoteView> get(@PathVariable Long id) {
        return DataResponse.of(service.adminGet(id));
    }

    @Operation(summary = "Qiymət təklif etmək", description = "Sətir qiymətlərini təyin edir, statusu QUOTED-ə keçirir.")
    @PostMapping("/{id}/quote")
    public DataResponse<QuoteDtos.QuoteView> submitQuote(@PathVariable Long id,
                                                         @Valid @RequestBody QuoteDtos.SubmitQuoteRequest request) {
        return DataResponse.of(service.submitQuote(id, request));
    }

    @Operation(summary = "Sorğunu rədd etmək")
    @PostMapping("/{id}/decline")
    public ResponseEntity<Void> decline(@PathVariable Long id, @RequestBody(required = false) QuoteDtos.DeclineRequest request) {
        service.adminDecline(id, request == null ? new QuoteDtos.DeclineRequest(null) : request);
        return ResponseEntity.noContent().build();
    }
}
