package az.catalog.modules.contact;

import az.catalog.common.dto.ApiResponses.DataResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Public · Əlaqə",
        description = """
                Açıq formalar. Hər üç endpoint honeypot sahəsi (`website`), IP üzrə
                rate limit və e-poçt üzrə saatlıq limit ilə qorunur.
                """)
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@SecurityRequirements
public class PublicContactController {

    private final ContactService service;

    @Operation(summary = "Əlaqə sorğusu göndərmək")
    @PostMapping("/contact-requests")
    public ResponseEntity<Void> submit(@Valid @RequestBody ContactDtos.SubmitRequest request,
                                       HttpServletRequest httpRequest) {
        service.submit(request, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Məhsul üzrə sorğu göndərmək",
            description = "Məhsul səhifəsindəki “Sorğu göndər” düyməsi bunu çağırır.")
    @PostMapping("/products/{id}/inquiry")
    public ResponseEntity<Void> inquiry(@PathVariable Long id,
                                        @Valid @RequestBody ContactDtos.InquiryRequest request,
                                        HttpServletRequest httpRequest) {
        service.submitInquiry(id, request, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Xəbər bülletenine abunə olmaq",
            description = """
                    İki mərhələli təsdiq: qeyd `PENDING` yaranır və yalnız e-poçtdakı
                    linkə klik edildikdən sonra `CONFIRMED` olur.

                    Təhlükəsizlik səbəbindən e-poçtun siyahıda olub-olmamasından asılı
                    olmayaraq həmişə 204 qaytarır.
                    """)
    @PostMapping("/newsletter-subscriptions")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody ContactDtos.NewsletterRequest request,
                                          HttpServletRequest httpRequest) {
        service.subscribe(request, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Abunəliyi təsdiqləmək")
    @GetMapping("/newsletter-subscriptions/confirm")
    public DataResponse<Map<String, Boolean>> confirm(@RequestParam String token) {
        return DataResponse.of(Map.of("confirmed", service.confirmSubscription(token)));
    }

    @Operation(summary = "Abunəlikdən çıxmaq")
    @PostMapping("/newsletter-subscriptions/unsubscribe")
    public DataResponse<Map<String, Boolean>> unsubscribe(@RequestParam String email) {
        return DataResponse.of(Map.of("unsubscribed", service.unsubscribe(email)));
    }
}
