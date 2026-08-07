package az.catalog.modules.content;

import az.catalog.common.config.RequestLanguage;
import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.modules.content.ContentEntities.Benefit;
import az.catalog.modules.content.ContentEntities.Faq;
import az.catalog.modules.content.ContentEntities.Partner;
import az.catalog.modules.content.ContentEntities.Statistic;
import az.catalog.modules.content.ContentEntities.Testimonial;
import az.catalog.modules.language.LanguageService;
import az.catalog.modules.media.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The content blocks page sections read from. All endpoints are language-resolved and
 * return only ACTIVE rows, so a section component can render whatever comes back without
 * further filtering.
 */
@Tag(name = "Public · Kontent")
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@SecurityRequirements
public class PublicContentController {

    private final BenefitRepository benefitRepository;
    private final StatisticRepository statisticRepository;
    private final TestimonialRepository testimonialRepository;
    private final PartnerRepository partnerRepository;
    private final FaqRepository faqRepository;
    private final LanguageService languageService;
    private final MediaService mediaService;

    // --- response shapes ----------------------------------------------------

    public record BenefitDto(Long id, String icon, String iconUrl, String title, String description) {
    }

    public record StatisticDto(Long id, String icon, String value, String suffix, String label) {
    }

    public record TestimonialDto(
            Long id, String personName, String position, String companyName,
            String photoUrl, Integer rating, String content, boolean featured) {
    }

    public record PartnerDto(Long id, String name, String logoUrl, String websiteUrl) {
    }

    public record FaqDto(Long id, String category, String question, String answer) {
    }

    // --- endpoints ----------------------------------------------------------

    @Operation(summary = "Üstünlüklər")
    @GetMapping("/benefits")
    @Transactional(readOnly = true)
    public DataResponse<List<BenefitDto>> benefits(@RequestLanguage String language) {
        String fallback = languageService.defaultLanguageCode();
        List<Benefit> benefits = benefitRepository.findActive();

        Map<Long, String> icons = mediaService.publicUrls(
                benefits.stream().map(Benefit::getIconMediaId).filter(Objects::nonNull).toList());

        return DataResponse.of(benefits.stream().map(benefit -> {
            var translation = pick(benefit.getTranslations(), language, fallback);
            return new BenefitDto(
                    benefit.getId(), benefit.getIcon(),
                    icons.get(benefit.getIconMediaId()),
                    translation == null ? "" : translation.getTitle(),
                    translation == null ? null : translation.getDescription());
        }).toList());
    }

    @Operation(summary = "Statistika")
    @GetMapping("/statistics")
    @Transactional(readOnly = true)
    public DataResponse<List<StatisticDto>> statistics(@RequestLanguage String language) {
        String fallback = languageService.defaultLanguageCode();

        return DataResponse.of(statisticRepository.findActive().stream().map(statistic -> {
            var translation = pick(statistic.getTranslations(), language, fallback);
            return new StatisticDto(
                    statistic.getId(), statistic.getIcon(), statistic.getValue(),
                    statistic.getSuffix(),
                    translation == null ? "" : translation.getLabel());
        }).toList());
    }

    @Operation(summary = "Müştəri rəyləri")
    @GetMapping("/testimonials")
    @Transactional(readOnly = true)
    public DataResponse<List<TestimonialDto>> testimonials(
            @RequestLanguage String language,
            @RequestParam(defaultValue = "12") int limit) {

        String fallback = languageService.defaultLanguageCode();
        List<Testimonial> testimonials =
                testimonialRepository.findActive(PageRequest.of(0, Math.min(limit, 48)));

        Map<Long, String> photos = mediaService.publicUrls(testimonials.stream()
                .map(Testimonial::getPhotoMediaId).filter(Objects::nonNull).toList());

        return DataResponse.of(testimonials.stream().map(testimonial -> {
            var translation = pick(testimonial.getTranslations(), language, fallback);
            return new TestimonialDto(
                    testimonial.getId(), testimonial.getPersonName(), testimonial.getPosition(),
                    testimonial.getCompanyName(), photos.get(testimonial.getPhotoMediaId()),
                    testimonial.getRating(),
                    translation == null ? "" : translation.getContent(),
                    testimonial.isFeatured());
        }).toList());
    }

    @Operation(summary = "Partnyorlar")
    @GetMapping("/partners")
    @Transactional(readOnly = true)
    public DataResponse<List<PartnerDto>> partners() {
        List<Partner> partners = partnerRepository.findActive();

        Map<Long, String> logos = mediaService.publicUrls(
                partners.stream().map(Partner::getLogoMediaId).filter(Objects::nonNull).toList());

        return DataResponse.of(partners.stream()
                .map(partner -> new PartnerDto(
                        partner.getId(), partner.getName(),
                        logos.get(partner.getLogoMediaId()), partner.getWebsiteUrl()))
                .toList());
    }

    @Operation(summary = "Tez-tez verilən suallar",
            description = "pageId göndərilsə həmin səhifəyə bağlı suallar + ümumi suallar qaytarılır.")
    @GetMapping("/faqs")
    @Transactional(readOnly = true)
    public DataResponse<List<FaqDto>> faqs(
            @RequestLanguage String language,
            @RequestParam(required = false) Long pageId,
            @RequestParam(required = false) String category) {

        String fallback = languageService.defaultLanguageCode();

        return DataResponse.of(faqRepository.findActive(pageId, category).stream().map(faq -> {
            var translation = pick(faq.getTranslations(), language, fallback);
            return new FaqDto(
                    faq.getId(), faq.getCategory(),
                    translation == null ? "" : translation.getQuestion(),
                    translation == null ? "" : translation.getAnswer());
        }).filter(dto -> !dto.question().isBlank()).toList());
    }

    /** Requested language, falling back to the site default, then to any translation. */
    private <T> T pick(Map<String, T> translations, String language, String fallback) {
        T match = translations.get(language);
        if (match != null) {
            return match;
        }
        T defaultMatch = translations.get(fallback);
        return defaultMatch != null
                ? defaultMatch
                : translations.values().stream().findFirst().orElse(null);
    }
}
