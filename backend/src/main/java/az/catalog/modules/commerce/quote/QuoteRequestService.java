package az.catalog.modules.commerce.quote;

import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.modules.catalog.product.Product;
import az.catalog.modules.catalog.product.ProductRepository;
import az.catalog.modules.commerce.company.CompanyMember;
import az.catalog.modules.commerce.company.CompanyMemberRepository;
import az.catalog.modules.commerce.customer.Customer;
import az.catalog.modules.commerce.customer.CustomerRepository;
import az.catalog.modules.language.LanguageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Request-for-quote: the negotiated-price counterpart to a normal checkout.
 *
 * Accepting a quote only flips its status — it does not spin up an {@code Order}. A
 * negotiated B2B deal usually still needs a human step (PO number, invoice terms), so the
 * sales team follows up manually rather than the system silently placing an order on the
 * customer's behalf for an amount they only verbally-equivalent agreed to.
 */
@Service
@RequiredArgsConstructor
public class QuoteRequestService {

    private final QuoteRequestRepository repository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final LanguageService languageService;

    // =======================================================================
    // Customer-facing
    // =======================================================================

    @Transactional
    public QuoteDtos.QuoteView create(Long customerId, QuoteDtos.CreateRequest request) {
        Customer customer = customerRepository.findAliveById(customerId)
                .orElseThrow(() -> ApiException.notFound("Müştəri", customerId));

        List<Long> productIds = request.items().stream().map(QuoteDtos.ItemRequest::productId).toList();
        Map<Long, Product> products = productRepository.findAllPublishedByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        String fallback = languageService.defaultLanguageCode();
        QuoteRequest quote = QuoteRequest.builder()
                .customer(customer)
                .company(companyMemberRepository.findByCustomerId(customerId)
                        .map(CompanyMember::getCompany).orElse(null))
                .contactName(request.contactName().trim())
                .contactEmail(request.contactEmail().trim().toLowerCase())
                .contactPhone(request.contactPhone())
                .note(request.note())
                .build();

        for (QuoteDtos.ItemRequest itemRequest : request.items()) {
            Product product = products.get(itemRequest.productId());
            if (product == null) {
                throw ApiException.notFound("Məhsul", itemRequest.productId());
            }
            var translation = product.translation(fallback, fallback);
            quote.getItems().add(QuoteRequestItem.builder()
                    .quoteRequest(quote)
                    .product(product)
                    .productName(translation != null ? translation.getName() : product.getSku())
                    .quantity(itemRequest.quantity())
                    .build());
        }

        return toView(repository.save(quote));
    }

    @Transactional(readOnly = true)
    public List<QuoteDtos.QuoteRow> mine(Long customerId) {
        return repository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(this::toRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuoteDtos.QuoteView mine(Long customerId, Long id) {
        return toView(loadForCustomer(id, customerId));
    }

    @Transactional
    public void accept(Long customerId, Long id) {
        QuoteRequest quote = loadForCustomer(id, customerId);
        if (quote.getStatus() != QuoteStatus.QUOTED) {
            throw ApiException.badRequest("Bu təklif hələ qiymətləndirilməyib");
        }
        if (quote.isExpired()) {
            quote.setStatus(QuoteStatus.EXPIRED);
            throw ApiException.badRequest("Təklifin etibarlılıq müddəti bitib");
        }
        quote.setStatus(QuoteStatus.ACCEPTED);
    }

    @Transactional
    public void decline(Long customerId, Long id) {
        QuoteRequest quote = loadForCustomer(id, customerId);
        quote.setStatus(QuoteStatus.DECLINED);
    }

    // =======================================================================
    // Admin
    // =======================================================================

    @Transactional(readOnly = true)
    public Page<QuoteDtos.QuoteRow> adminSearch(QuoteStatus status, String search, Pageable pageable) {
        return repository.search(status, blankToNull(search), pageable).map(this::toRow);
    }

    @Transactional(readOnly = true)
    public QuoteDtos.QuoteView adminGet(Long id) {
        return toView(load(id));
    }

    @Transactional
    public QuoteDtos.QuoteView submitQuote(Long id, QuoteDtos.SubmitQuoteRequest request) {
        QuoteRequest quote = load(id);
        Map<Long, BigDecimal> prices = request.prices().stream()
                .collect(Collectors.toMap(QuoteDtos.ItemPriceInput::itemId,
                        QuoteDtos.ItemPriceInput::unitPrice));

        BigDecimal total = BigDecimal.ZERO;
        for (QuoteRequestItem item : quote.getItems()) {
            BigDecimal price = prices.get(item.getId());
            if (price != null) {
                item.setQuotedUnitPrice(price);
            }
            if (item.getQuotedUnitPrice() != null) {
                total = total.add(item.getQuotedUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }

        quote.setQuotedTotal(total);
        quote.setValidUntil(request.validUntil());
        quote.setAdminNote(request.adminNote());
        quote.setStatus(QuoteStatus.QUOTED);

        return toView(quote);
    }

    @Transactional
    public void adminDecline(Long id, QuoteDtos.DeclineRequest request) {
        QuoteRequest quote = load(id);
        quote.setStatus(QuoteStatus.DECLINED);
        quote.setAdminNote(request.adminNote());
    }

    // =======================================================================
    // Internals
    // =======================================================================

    private QuoteRequest load(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Təklif sorğusu", id));
    }

    private QuoteRequest loadForCustomer(Long id, Long customerId) {
        return repository.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Sorğu tapılmadı"));
    }

    private QuoteDtos.QuoteView toView(QuoteRequest quote) {
        List<QuoteDtos.ItemView> items = quote.getItems().stream()
                .map(item -> new QuoteDtos.ItemView(
                        item.getId(),
                        item.getProduct() == null ? null : item.getProduct().getId(),
                        item.getProductName(),
                        item.getProduct() == null ? null : item.getProduct().getSlug(),
                        item.getQuantity(),
                        item.getQuotedUnitPrice(),
                        item.getQuotedUnitPrice() == null ? null
                                : item.getQuotedUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))))
                .toList();

        return new QuoteDtos.QuoteView(quote.getId(), quote.getContactName(), quote.getContactEmail(),
                quote.getContactPhone(), quote.getNote(), quote.getStatus(), quote.getAdminNote(),
                quote.getValidUntil(), quote.getQuotedTotal(), quote.getCurrency(), items, quote.getCreatedAt());
    }

    private QuoteDtos.QuoteRow toRow(QuoteRequest quote) {
        return new QuoteDtos.QuoteRow(quote.getId(), quote.getContactName(), quote.getContactEmail(),
                quote.getStatus(), quote.getItems().size(), quote.getQuotedTotal(), quote.getCurrency(),
                quote.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
