package az.catalog.modules.contact;

import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.dto.ApiResponses.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@Tag(name = "Admin · Əlaqə sorğuları")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','SUPPORT')")
public class AdminContactController {

    private final ContactService service;

    @Operation(summary = "Sorğu siyahısı")
    @GetMapping("/contact-requests")
    public PageResponse<ContactDtos.RequestRow> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ContactRequest.Status status,
            @RequestParam(required = false) ContactRequest.RequestType type,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdTo,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.of(service.search(search, status, type, createdFrom, createdTo, pageable));
    }

    @Operation(summary = "Sorğu detalı")
    @GetMapping("/contact-requests/{id}")
    public DataResponse<ContactDtos.RequestDetail> get(@PathVariable Long id) {
        return DataResponse.of(service.get(id));
    }

    @Operation(summary = "Status dəyişmək",
            description = "NEW-dən çıxarıldıqda sorğu əməliyyatı edən operatora bağlanır.")
    @PatchMapping("/contact-requests/{id}/status")
    public DataResponse<ContactDtos.RequestDetail> changeStatus(
            @PathVariable Long id, @Valid @RequestBody ContactDtos.StatusRequest request) {
        return DataResponse.of(service.changeStatus(id, request.status()));
    }

    @Operation(summary = "Daxili qeyd yazmaq")
    @PutMapping("/contact-requests/{id}/note")
    public DataResponse<ContactDtos.RequestDetail> setNote(
            @PathVariable Long id, @Valid @RequestBody ContactDtos.NoteRequest request) {
        return DataResponse.of(service.setNote(id, request.note()));
    }

    @Operation(summary = "Sorğunu silmək")
    @DeleteMapping("/contact-requests/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Sayğaclar", description = "Filtr çipləri üçün status üzrə saylar.")
    @GetMapping("/contact-requests/counters")
    public DataResponse<ContactDtos.Counters> counters() {
        return DataResponse.of(service.counters());
    }

    @Operation(summary = "Xəbər bülleteni abunəçiləri")
    @GetMapping("/newsletter-subscriptions")
    public PageResponse<ContactDtos.SubscriberRow> subscribers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) NewsletterSubscription.Status status,
            @PageableDefault(size = 50) Pageable pageable) {
        return PageResponse.of(service.subscribers(search, status, pageable));
    }
}
