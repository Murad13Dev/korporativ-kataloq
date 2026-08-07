package az.catalog.modules.content;

import az.catalog.common.dto.ApiResponses.DataResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin CRUD for the corporate content blocks the public sections read from
 * (see {@link PublicContentController}).
 */
@Tag(name = "Admin · Kontent")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','EDITOR')")
public class AdminContentController {

    private final AdminContentService service;

    // --- benefits ------------------------------------------------------------

    @Operation(summary = "Üstünlüklər siyahısı")
    @GetMapping("/benefits")
    public DataResponse<List<ContentDtos.BenefitResponse>> benefits() {
        return DataResponse.of(service.listBenefits());
    }

    @Operation(summary = "Üstünlük yaratmaq")
    @PostMapping("/benefits")
    public DataResponse<ContentDtos.BenefitResponse> createBenefit(
            @Valid @RequestBody ContentDtos.BenefitRequest request) {
        return DataResponse.of(service.createBenefit(request));
    }

    @Operation(summary = "Üstünlüyü redaktə etmək")
    @PutMapping("/benefits/{id}")
    public DataResponse<ContentDtos.BenefitResponse> updateBenefit(
            @PathVariable Long id, @Valid @RequestBody ContentDtos.BenefitRequest request) {
        return DataResponse.of(service.updateBenefit(id, request));
    }

    @Operation(summary = "Üstünlüyü silmək")
    @DeleteMapping("/benefits/{id}")
    public ResponseEntity<Void> deleteBenefit(@PathVariable Long id) {
        service.deleteBenefit(id);
        return ResponseEntity.noContent().build();
    }

    // --- statistics ------------------------------------------------------------

    @Operation(summary = "Statistika siyahısı")
    @GetMapping("/statistics")
    public DataResponse<List<ContentDtos.StatisticResponse>> statistics() {
        return DataResponse.of(service.listStatistics());
    }

    @Operation(summary = "Statistika yaratmaq")
    @PostMapping("/statistics")
    public DataResponse<ContentDtos.StatisticResponse> createStatistic(
            @Valid @RequestBody ContentDtos.StatisticRequest request) {
        return DataResponse.of(service.createStatistic(request));
    }

    @Operation(summary = "Statistikanı redaktə etmək")
    @PutMapping("/statistics/{id}")
    public DataResponse<ContentDtos.StatisticResponse> updateStatistic(
            @PathVariable Long id, @Valid @RequestBody ContentDtos.StatisticRequest request) {
        return DataResponse.of(service.updateStatistic(id, request));
    }

    @Operation(summary = "Statistikanı silmək")
    @DeleteMapping("/statistics/{id}")
    public ResponseEntity<Void> deleteStatistic(@PathVariable Long id) {
        service.deleteStatistic(id);
        return ResponseEntity.noContent().build();
    }

    // --- testimonials ------------------------------------------------------------

    @Operation(summary = "Müştəri rəyləri siyahısı")
    @GetMapping("/testimonials")
    public DataResponse<List<ContentDtos.TestimonialResponse>> testimonials() {
        return DataResponse.of(service.listTestimonials());
    }

    @Operation(summary = "Rəy yaratmaq")
    @PostMapping("/testimonials")
    public DataResponse<ContentDtos.TestimonialResponse> createTestimonial(
            @Valid @RequestBody ContentDtos.TestimonialRequest request) {
        return DataResponse.of(service.createTestimonial(request));
    }

    @Operation(summary = "Rəyi redaktə etmək")
    @PutMapping("/testimonials/{id}")
    public DataResponse<ContentDtos.TestimonialResponse> updateTestimonial(
            @PathVariable Long id, @Valid @RequestBody ContentDtos.TestimonialRequest request) {
        return DataResponse.of(service.updateTestimonial(id, request));
    }

    @Operation(summary = "Rəyi silmək")
    @DeleteMapping("/testimonials/{id}")
    public ResponseEntity<Void> deleteTestimonial(@PathVariable Long id) {
        service.deleteTestimonial(id);
        return ResponseEntity.noContent().build();
    }

    // --- partners ------------------------------------------------------------

    @Operation(summary = "Partnyorlar siyahısı")
    @GetMapping("/partners")
    public DataResponse<List<ContentDtos.PartnerResponse>> partners() {
        return DataResponse.of(service.listPartners());
    }

    @Operation(summary = "Partnyor yaratmaq")
    @PostMapping("/partners")
    public DataResponse<ContentDtos.PartnerResponse> createPartner(
            @Valid @RequestBody ContentDtos.PartnerRequest request) {
        return DataResponse.of(service.createPartner(request));
    }

    @Operation(summary = "Partnyoru redaktə etmək")
    @PutMapping("/partners/{id}")
    public DataResponse<ContentDtos.PartnerResponse> updatePartner(
            @PathVariable Long id, @Valid @RequestBody ContentDtos.PartnerRequest request) {
        return DataResponse.of(service.updatePartner(id, request));
    }

    @Operation(summary = "Partnyoru silmək")
    @DeleteMapping("/partners/{id}")
    public ResponseEntity<Void> deletePartner(@PathVariable Long id) {
        service.deletePartner(id);
        return ResponseEntity.noContent().build();
    }

    // --- faqs ------------------------------------------------------------

    @Operation(summary = "Tez-tez verilən suallar siyahısı")
    @GetMapping("/faqs")
    public DataResponse<List<ContentDtos.FaqResponse>> faqs() {
        return DataResponse.of(service.listFaqs());
    }

    @Operation(summary = "Sual yaratmaq")
    @PostMapping("/faqs")
    public DataResponse<ContentDtos.FaqResponse> createFaq(
            @Valid @RequestBody ContentDtos.FaqRequest request) {
        return DataResponse.of(service.createFaq(request));
    }

    @Operation(summary = "Sualı redaktə etmək")
    @PutMapping("/faqs/{id}")
    public DataResponse<ContentDtos.FaqResponse> updateFaq(
            @PathVariable Long id, @Valid @RequestBody ContentDtos.FaqRequest request) {
        return DataResponse.of(service.updateFaq(id, request));
    }

    @Operation(summary = "Sualı silmək")
    @DeleteMapping("/faqs/{id}")
    public ResponseEntity<Void> deleteFaq(@PathVariable Long id) {
        service.deleteFaq(id);
        return ResponseEntity.noContent().build();
    }
}
