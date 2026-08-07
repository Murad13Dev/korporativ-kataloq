package az.catalog.modules.commerce.quote;

import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Customer · Təklif sorğuları")
@RestController
@RequestMapping("/api/customer/quote-requests")
@RequiredArgsConstructor
public class CustomerQuoteController {

    private final QuoteRequestService service;

    @Operation(summary = "Təklif sorğusu göndərmək")
    @PostMapping
    public DataResponse<QuoteDtos.QuoteView> create(@Valid @RequestBody QuoteDtos.CreateRequest request) {
        return DataResponse.of(service.create(SecurityUtils.requireCustomer().id(), request));
    }

    @Operation(summary = "Mənim sorğularım")
    @GetMapping
    public DataResponse<List<QuoteDtos.QuoteRow>> mine() {
        return DataResponse.of(service.mine(SecurityUtils.requireCustomer().id()));
    }

    @Operation(summary = "Sorğu detalı")
    @GetMapping("/{id}")
    public DataResponse<QuoteDtos.QuoteView> get(@PathVariable Long id) {
        return DataResponse.of(service.mine(SecurityUtils.requireCustomer().id(), id));
    }

    @Operation(summary = "Təklifi qəbul etmək")
    @PostMapping("/{id}/accept")
    public ResponseEntity<Void> accept(@PathVariable Long id) {
        service.accept(SecurityUtils.requireCustomer().id(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Təklifi rədd etmək")
    @PostMapping("/{id}/decline")
    public ResponseEntity<Void> decline(@PathVariable Long id) {
        service.decline(SecurityUtils.requireCustomer().id(), id);
        return ResponseEntity.noContent().build();
    }
}
