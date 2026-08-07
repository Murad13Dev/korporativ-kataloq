package az.catalog.modules.content;

import az.catalog.common.domain.ContentStatus;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Admin request/response shapes for the five content blocks. Grouped for the same reason
 * as {@link ContentEntities}: five files with an identical shape would obscure the pattern.
 */
public final class ContentDtos {

    private ContentDtos() {
    }

    // --- benefit --------------------------------------------------------------

    public record BenefitTranslationDto(String title, String description) {
    }

    public record BenefitResponse(
            Long id, String icon, Long iconMediaId, String iconUrl,
            ContentStatus status, int sortOrder,
            Map<String, BenefitTranslationDto> translations) {
    }

    public record BenefitRequest(
            String icon, Long iconMediaId, ContentStatus status, Integer sortOrder,
            Map<String, BenefitTranslationDto> translations) {
    }

    // --- statistic --------------------------------------------------------------

    public record StatisticTranslationDto(String label) {
    }

    public record StatisticResponse(
            Long id, String icon, String value, String suffix,
            ContentStatus status, int sortOrder,
            Map<String, StatisticTranslationDto> translations) {
    }

    public record StatisticRequest(
            String icon, @NotBlank String value, String suffix,
            ContentStatus status, Integer sortOrder,
            Map<String, StatisticTranslationDto> translations) {
    }

    // --- testimonial --------------------------------------------------------------

    public record TestimonialTranslationDto(String content) {
    }

    public record TestimonialResponse(
            Long id, String personName, String position, String companyName,
            Long photoMediaId, String photoUrl, Integer rating, boolean featured,
            ContentStatus status, int sortOrder,
            Map<String, TestimonialTranslationDto> translations) {
    }

    public record TestimonialRequest(
            @NotBlank String personName, String position, String companyName,
            Long photoMediaId, Integer rating, Boolean featured,
            ContentStatus status, Integer sortOrder,
            Map<String, TestimonialTranslationDto> translations) {
    }

    // --- partner --------------------------------------------------------------
    // No translations: a company name is a proper noun.

    public record PartnerResponse(
            Long id, String name, Long logoMediaId, String logoUrl, String websiteUrl,
            ContentStatus status, int sortOrder) {
    }

    public record PartnerRequest(
            @NotBlank String name, Long logoMediaId, String websiteUrl,
            ContentStatus status, Integer sortOrder) {
    }

    // --- faq --------------------------------------------------------------

    public record FaqTranslationDto(String question, String answer) {
    }

    public record FaqResponse(
            Long id, Long pageId, String category, ContentStatus status, int sortOrder,
            Map<String, FaqTranslationDto> translations) {
    }

    public record FaqRequest(
            Long pageId, String category, ContentStatus status, Integer sortOrder,
            Map<String, FaqTranslationDto> translations) {
    }
}
