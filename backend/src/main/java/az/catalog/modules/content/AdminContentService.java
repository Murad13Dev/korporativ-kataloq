package az.catalog.modules.content;

import az.catalog.common.domain.ContentStatus;
import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.common.util.HtmlSanitizer;
import az.catalog.modules.content.ContentEntities.Benefit;
import az.catalog.modules.content.ContentEntities.BenefitTranslation;
import az.catalog.modules.content.ContentEntities.Faq;
import az.catalog.modules.content.ContentEntities.FaqTranslation;
import az.catalog.modules.content.ContentEntities.Partner;
import az.catalog.modules.content.ContentEntities.Statistic;
import az.catalog.modules.content.ContentEntities.StatisticTranslation;
import az.catalog.modules.content.ContentEntities.Testimonial;
import az.catalog.modules.content.ContentEntities.TestimonialTranslation;
import az.catalog.modules.language.LanguageService;
import az.catalog.modules.media.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin CRUD for the five corporate content blocks. Every type follows the same shape —
 * status, sort order, per-language translations — so the write path is the same four
 * operations five times rather than a shared abstraction; the abstraction would have to
 * take a translation-field-count parameter, which is not simpler than five short methods.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminContentService {

    private final BenefitRepository benefitRepository;
    private final StatisticRepository statisticRepository;
    private final TestimonialRepository testimonialRepository;
    private final PartnerRepository partnerRepository;
    private final FaqRepository faqRepository;
    private final LanguageService languageService;
    private final MediaService mediaService;
    private final HtmlSanitizer sanitizer;

    // =======================================================================
    // Benefit
    // =======================================================================

    @Transactional(readOnly = true)
    public List<ContentDtos.BenefitResponse> listBenefits() {
        return benefitRepository.findAllOrdered().stream().map(this::toBenefitResponse).toList();
    }

    @Transactional
    public ContentDtos.BenefitResponse createBenefit(ContentDtos.BenefitRequest request) {
        Benefit benefit = new Benefit();
        benefit.setIcon(request.icon());
        benefit.setIconMediaId(request.iconMediaId());
        benefit.setStatus(request.status() == null ? ContentStatus.ACTIVE : request.status());
        benefit.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        applyBenefitTranslations(benefit, request.translations());
        return toBenefitResponse(benefitRepository.save(benefit));
    }

    @Transactional
    public ContentDtos.BenefitResponse updateBenefit(Long id, ContentDtos.BenefitRequest request) {
        Benefit benefit = benefitRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Üstünlük", id));
        benefit.setIcon(request.icon());
        benefit.setIconMediaId(request.iconMediaId());
        benefit.setStatus(request.status() == null ? benefit.getStatus() : request.status());
        if (request.sortOrder() != null) {
            benefit.setSortOrder(request.sortOrder());
        }
        applyBenefitTranslations(benefit, request.translations());
        return toBenefitResponse(benefit);
    }

    @Transactional
    public void deleteBenefit(Long id) {
        benefitRepository.deleteById(id);
    }

    private void applyBenefitTranslations(Benefit benefit, Map<String, ContentDtos.BenefitTranslationDto> translations) {
        Set<String> active = Set.copyOf(languageService.activeLanguageCodes());
        benefit.getTranslations().keySet().removeIf(code -> !translations.containsKey(code));
        translations.forEach((code, dto) -> {
            if (!active.contains(code)) {
                throw new ApiException(ErrorCode.LANGUAGE_NOT_ACTIVE, "Dil aktiv deyil: " + code);
            }
            BenefitTranslation translation = benefit.getTranslations().getOrDefault(code,
                    BenefitTranslation.builder().benefit(benefit).languageCode(code).build());
            translation.setTitle(sanitizer.stripTags(dto.title()).trim());
            translation.setDescription(sanitizer.stripTags(dto.description()));
            benefit.getTranslations().put(code, translation);
        });
    }

    private ContentDtos.BenefitResponse toBenefitResponse(Benefit benefit) {
        Map<String, ContentDtos.BenefitTranslationDto> translations = new LinkedHashMap<>();
        benefit.getTranslations().forEach((code, translation) -> translations.put(code,
                new ContentDtos.BenefitTranslationDto(translation.getTitle(), translation.getDescription())));
        return new ContentDtos.BenefitResponse(
                benefit.getId(), benefit.getIcon(), benefit.getIconMediaId(),
                benefit.getIconMediaId() == null ? null : mediaService.publicUrl(benefit.getIconMediaId()),
                benefit.getStatus(), benefit.getSortOrder(), translations);
    }

    // =======================================================================
    // Statistic
    // =======================================================================

    @Transactional(readOnly = true)
    public List<ContentDtos.StatisticResponse> listStatistics() {
        return statisticRepository.findAllOrdered().stream().map(this::toStatisticResponse).toList();
    }

    @Transactional
    public ContentDtos.StatisticResponse createStatistic(ContentDtos.StatisticRequest request) {
        Statistic statistic = new Statistic();
        statistic.setIcon(request.icon());
        statistic.setValue(request.value());
        statistic.setSuffix(request.suffix());
        statistic.setStatus(request.status() == null ? ContentStatus.ACTIVE : request.status());
        statistic.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        applyStatisticTranslations(statistic, request.translations());
        return toStatisticResponse(statisticRepository.save(statistic));
    }

    @Transactional
    public ContentDtos.StatisticResponse updateStatistic(Long id, ContentDtos.StatisticRequest request) {
        Statistic statistic = statisticRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Statistika", id));
        statistic.setIcon(request.icon());
        statistic.setValue(request.value());
        statistic.setSuffix(request.suffix());
        statistic.setStatus(request.status() == null ? statistic.getStatus() : request.status());
        if (request.sortOrder() != null) {
            statistic.setSortOrder(request.sortOrder());
        }
        applyStatisticTranslations(statistic, request.translations());
        return toStatisticResponse(statistic);
    }

    @Transactional
    public void deleteStatistic(Long id) {
        statisticRepository.deleteById(id);
    }

    private void applyStatisticTranslations(Statistic statistic, Map<String, ContentDtos.StatisticTranslationDto> translations) {
        Set<String> active = Set.copyOf(languageService.activeLanguageCodes());
        statistic.getTranslations().keySet().removeIf(code -> !translations.containsKey(code));
        translations.forEach((code, dto) -> {
            if (!active.contains(code)) {
                throw new ApiException(ErrorCode.LANGUAGE_NOT_ACTIVE, "Dil aktiv deyil: " + code);
            }
            StatisticTranslation translation = statistic.getTranslations().getOrDefault(code,
                    StatisticTranslation.builder().statistic(statistic).languageCode(code).build());
            translation.setLabel(sanitizer.stripTags(dto.label()).trim());
            statistic.getTranslations().put(code, translation);
        });
    }

    private ContentDtos.StatisticResponse toStatisticResponse(Statistic statistic) {
        Map<String, ContentDtos.StatisticTranslationDto> translations = new LinkedHashMap<>();
        statistic.getTranslations().forEach((code, translation) -> translations.put(code,
                new ContentDtos.StatisticTranslationDto(translation.getLabel())));
        return new ContentDtos.StatisticResponse(
                statistic.getId(), statistic.getIcon(), statistic.getValue(), statistic.getSuffix(),
                statistic.getStatus(), statistic.getSortOrder(), translations);
    }

    // =======================================================================
    // Testimonial
    // =======================================================================

    @Transactional(readOnly = true)
    public List<ContentDtos.TestimonialResponse> listTestimonials() {
        return testimonialRepository.findAllOrdered().stream().map(this::toTestimonialResponse).toList();
    }

    @Transactional
    public ContentDtos.TestimonialResponse createTestimonial(ContentDtos.TestimonialRequest request) {
        Testimonial testimonial = new Testimonial();
        applyTestimonialScalars(testimonial, request);
        applyTestimonialTranslations(testimonial, request.translations());
        return toTestimonialResponse(testimonialRepository.save(testimonial));
    }

    @Transactional
    public ContentDtos.TestimonialResponse updateTestimonial(Long id, ContentDtos.TestimonialRequest request) {
        Testimonial testimonial = testimonialRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Rəy", id));
        applyTestimonialScalars(testimonial, request);
        applyTestimonialTranslations(testimonial, request.translations());
        return toTestimonialResponse(testimonial);
    }

    @Transactional
    public void deleteTestimonial(Long id) {
        testimonialRepository.deleteById(id);
    }

    private void applyTestimonialScalars(Testimonial testimonial, ContentDtos.TestimonialRequest request) {
        testimonial.setPersonName(request.personName().trim());
        testimonial.setPosition(request.position());
        testimonial.setCompanyName(request.companyName());
        testimonial.setPhotoMediaId(request.photoMediaId());
        testimonial.setRating(request.rating());
        testimonial.setFeatured(Boolean.TRUE.equals(request.featured()));
        testimonial.setStatus(request.status() == null
                ? (testimonial.getStatus() == null ? ContentStatus.ACTIVE : testimonial.getStatus())
                : request.status());
        if (request.sortOrder() != null) {
            testimonial.setSortOrder(request.sortOrder());
        }
    }

    private void applyTestimonialTranslations(Testimonial testimonial, Map<String, ContentDtos.TestimonialTranslationDto> translations) {
        Set<String> active = Set.copyOf(languageService.activeLanguageCodes());
        testimonial.getTranslations().keySet().removeIf(code -> !translations.containsKey(code));
        translations.forEach((code, dto) -> {
            if (!active.contains(code)) {
                throw new ApiException(ErrorCode.LANGUAGE_NOT_ACTIVE, "Dil aktiv deyil: " + code);
            }
            TestimonialTranslation translation = testimonial.getTranslations().getOrDefault(code,
                    TestimonialTranslation.builder().testimonial(testimonial).languageCode(code).build());
            translation.setContent(sanitizer.stripTags(dto.content()).trim());
            testimonial.getTranslations().put(code, translation);
        });
    }

    private ContentDtos.TestimonialResponse toTestimonialResponse(Testimonial testimonial) {
        Map<String, ContentDtos.TestimonialTranslationDto> translations = new LinkedHashMap<>();
        testimonial.getTranslations().forEach((code, translation) -> translations.put(code,
                new ContentDtos.TestimonialTranslationDto(translation.getContent())));
        Long photoId = testimonial.getPhotoMediaId();
        return new ContentDtos.TestimonialResponse(
                testimonial.getId(), testimonial.getPersonName(), testimonial.getPosition(),
                testimonial.getCompanyName(), photoId,
                photoId == null ? null : mediaService.publicUrl(photoId),
                testimonial.getRating(), testimonial.isFeatured(),
                testimonial.getStatus(), testimonial.getSortOrder(), translations);
    }

    // =======================================================================
    // Partner
    // =======================================================================

    @Transactional(readOnly = true)
    public List<ContentDtos.PartnerResponse> listPartners() {
        return partnerRepository.findAllOrdered().stream().map(this::toPartnerResponse).toList();
    }

    @Transactional
    public ContentDtos.PartnerResponse createPartner(ContentDtos.PartnerRequest request) {
        Partner partner = new Partner();
        applyPartnerScalars(partner, request);
        return toPartnerResponse(partnerRepository.save(partner));
    }

    @Transactional
    public ContentDtos.PartnerResponse updatePartner(Long id, ContentDtos.PartnerRequest request) {
        Partner partner = partnerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Partnyor", id));
        applyPartnerScalars(partner, request);
        return toPartnerResponse(partner);
    }

    @Transactional
    public void deletePartner(Long id) {
        partnerRepository.deleteById(id);
    }

    private void applyPartnerScalars(Partner partner, ContentDtos.PartnerRequest request) {
        partner.setName(sanitizer.stripTags(request.name()).trim());
        partner.setWebsiteUrl(request.websiteUrl());
        partner.setLogoMediaId(request.logoMediaId());
        partner.setStatus(request.status() == null
                ? (partner.getStatus() == null ? ContentStatus.ACTIVE : partner.getStatus())
                : request.status());
        if (request.sortOrder() != null) {
            partner.setSortOrder(request.sortOrder());
        }
    }

    private ContentDtos.PartnerResponse toPartnerResponse(Partner partner) {
        Long logoId = partner.getLogoMediaId();
        return new ContentDtos.PartnerResponse(
                partner.getId(), partner.getName(), logoId,
                logoId == null ? null : mediaService.publicUrl(logoId),
                partner.getWebsiteUrl(), partner.getStatus(), partner.getSortOrder());
    }

    // =======================================================================
    // FAQ
    // =======================================================================

    @Transactional(readOnly = true)
    public List<ContentDtos.FaqResponse> listFaqs() {
        return faqRepository.findAllOrdered().stream().map(this::toFaqResponse).toList();
    }

    @Transactional
    public ContentDtos.FaqResponse createFaq(ContentDtos.FaqRequest request) {
        Faq faq = new Faq();
        applyFaqScalars(faq, request);
        applyFaqTranslations(faq, request.translations());
        return toFaqResponse(faqRepository.save(faq));
    }

    @Transactional
    public ContentDtos.FaqResponse updateFaq(Long id, ContentDtos.FaqRequest request) {
        Faq faq = faqRepository.findById(id).orElseThrow(() -> ApiException.notFound("Sual", id));
        applyFaqScalars(faq, request);
        applyFaqTranslations(faq, request.translations());
        return toFaqResponse(faq);
    }

    @Transactional
    public void deleteFaq(Long id) {
        faqRepository.deleteById(id);
    }

    private void applyFaqScalars(Faq faq, ContentDtos.FaqRequest request) {
        faq.setPageId(request.pageId());
        faq.setCategory(request.category());
        faq.setStatus(request.status() == null
                ? (faq.getStatus() == null ? ContentStatus.ACTIVE : faq.getStatus())
                : request.status());
        if (request.sortOrder() != null) {
            faq.setSortOrder(request.sortOrder());
        }
    }

    private void applyFaqTranslations(Faq faq, Map<String, ContentDtos.FaqTranslationDto> translations) {
        Set<String> active = Set.copyOf(languageService.activeLanguageCodes());
        faq.getTranslations().keySet().removeIf(code -> !translations.containsKey(code));
        translations.forEach((code, dto) -> {
            if (!active.contains(code)) {
                throw new ApiException(ErrorCode.LANGUAGE_NOT_ACTIVE, "Dil aktiv deyil: " + code);
            }
            FaqTranslation translation = faq.getTranslations().getOrDefault(code,
                    FaqTranslation.builder().faq(faq).languageCode(code).build());
            translation.setQuestion(sanitizer.stripTags(dto.question()).trim());
            translation.setAnswer(sanitizer.stripTags(dto.answer()).trim());
            faq.getTranslations().put(code, translation);
        });
    }

    private ContentDtos.FaqResponse toFaqResponse(Faq faq) {
        Map<String, ContentDtos.FaqTranslationDto> translations = new LinkedHashMap<>();
        faq.getTranslations().forEach((code, translation) -> translations.put(code,
                new ContentDtos.FaqTranslationDto(translation.getQuestion(), translation.getAnswer())));
        return new ContentDtos.FaqResponse(
                faq.getId(), faq.getPageId(), faq.getCategory(),
                faq.getStatus(), faq.getSortOrder(), translations);
    }
}
