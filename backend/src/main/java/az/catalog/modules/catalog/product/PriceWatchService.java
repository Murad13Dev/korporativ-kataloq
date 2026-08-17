package az.catalog.modules.catalog.product;

import az.catalog.common.config.AppProperties;
import az.catalog.common.exception.ApiException;
import az.catalog.modules.catalog.discount.DiscountResolver;
import az.catalog.modules.language.LanguageService;
import az.catalog.modules.notification.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * "Tell me when this goes on sale" — mirrors {@code ProductService}'s stock-notify flow
 * exactly, just watching the discount set instead of stock quantity. Deliberately has no
 * target price: it fires the moment *any* campaign makes the product cheaper, which is a
 * simpler promise to make (and to keep) than "notify me below X manat".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceWatchService {

    private final PriceWatchRepository repository;
    private final ProductRepository productRepository;
    private final DiscountResolver discountResolver;
    private final MailService mailService;
    private final LanguageService languageService;
    private final AppProperties properties;

    @Transactional
    public void subscribe(String slug, String email) {
        Product product = productRepository.findPublishedBySlug(slug)
                .orElseThrow(() -> ApiException.notFoundBySlug("Məhsul", slug));
        String normalised = email.trim().toLowerCase();
        if (repository.existsByProductIdAndEmailIgnoreCase(product.getId(), normalised)) {
            return;
        }
        repository.save(PriceWatch.builder()
                .product(product)
                .email(normalised)
                .createdAt(OffsetDateTime.now())
                .build());
    }

    /** Every 30 minutes is plenty for a "campaign started" signal — no shopper expects
        the email within the minute a discount goes live. */
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    @Transactional
    public void sweep() {
        List<Long> discountedIds = discountResolver.discountedProductIds();
        if (discountedIds.isEmpty()) {
            return;
        }
        List<PriceWatch> pending = repository.findByProductIdInAndNotifiedAtIsNull(discountedIds);
        if (pending.isEmpty()) {
            return;
        }

        String fallback = languageService.defaultLanguageCode();
        OffsetDateTime now = OffsetDateTime.now();

        for (PriceWatch watch : pending) {
            Product product = watch.getProduct();
            var translation = product.translation(fallback, fallback);
            String name = translation != null ? translation.getName() : product.getSku();
            String link = properties.getPublicUrl() + "/mehsul/" + product.getSlug();

            mailService.sendPriceDrop(watch.getEmail(), name, link);
            watch.setNotifiedAt(now);
        }
        log.info("Price-drop mail sent for {} watch(es)", pending.size());
    }
}
