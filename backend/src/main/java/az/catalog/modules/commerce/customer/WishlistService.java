package az.catalog.modules.commerce.customer;

import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.modules.catalog.product.Product;
import az.catalog.modules.catalog.product.ProductDtos;
import az.catalog.modules.catalog.product.ProductRepository;
import az.catalog.modules.catalog.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Saved-for-later products. Requires a signed-in customer — unlike the cart, a wishlist
 * has no guest/session identity to hang entries off, so it is only ever reached through
 * the authenticated {@code /api/customer} surface.
 */
@Service
@RequiredArgsConstructor
public class WishlistService {

    private static final int MAX_ITEMS = 200;
    private static final String TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TOKEN_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WishlistRepository wishlistRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;

    @Transactional(readOnly = true)
    public List<ProductDtos.ProductCard> list(Long customerId, String language) {
        List<Product> products = wishlistRepository.findAllByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(WishlistItem::getProduct)
                .toList();
        return productService.cardsFor(products, language);
    }

    /** Idempotent: adding an already-saved product is a no-op, not an error. */
    @Transactional
    public void add(Long customerId, Long productId) {
        if (wishlistRepository.existsByCustomerIdAndProductId(customerId, productId)) {
            return;
        }
        if (wishlistRepository.countByCustomerId(customerId) >= MAX_ITEMS) {
            throw ApiException.badRequest(
                    "Sevimlilər siyahısında maksimum %d məhsul saxlaya bilərsiniz".formatted(MAX_ITEMS));
        }
        Product product = productRepository.findAliveById(productId)
                .orElseThrow(() -> ApiException.notFound("Məhsul", productId));

        wishlistRepository.save(WishlistItem.builder()
                .customer(customerRepository.getReferenceById(customerId))
                .product(product)
                .createdAt(OffsetDateTime.now())
                .build());
    }

    /** Idempotent: removing a product that isn't saved is a no-op, not an error. */
    @Transactional
    public void remove(Long customerId, Long productId) {
        wishlistRepository.findByCustomerIdAndProductId(customerId, productId)
                .ifPresent(wishlistRepository::delete);
    }

    /**
     * Returns the customer's existing share token, minting one on first use. Token,
     * not customer id, is the capability: knowing it is what lets a stranger view the
     * list, so it must never be guessable from the customer's numeric id.
     */
    @Transactional
    public String getOrCreateShareToken(Long customerId) {
        Customer customer = customerRepository.findAliveById(customerId)
                .orElseThrow(() -> ApiException.notFound("Müştəri", customerId));
        if (customer.getWishlistShareToken() == null) {
            customer.setWishlistShareToken(generateToken());
        }
        return customer.getWishlistShareToken();
    }

    /** Public, read-only — the token alone is the whole access check. An unknown token
        looks exactly like an empty wishlist rather than revealing whether it ever
        existed. */
    @Transactional(readOnly = true)
    public List<ProductDtos.ProductCard> byShareToken(String token, String language) {
        return customerRepository.findByWishlistShareTokenIgnoreCase(token)
                .map(customer -> list(customer.getId(), language))
                .orElse(List.of());
    }

    private String generateToken() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder token = new StringBuilder(TOKEN_LENGTH);
            for (int i = 0; i < TOKEN_LENGTH; i++) {
                token.append(TOKEN_ALPHABET.charAt(RANDOM.nextInt(TOKEN_ALPHABET.length())));
            }
            String candidate = token.toString();
            if (!customerRepository.existsByWishlistShareTokenIgnoreCase(candidate)) {
                return candidate;
            }
        }
        throw new ApiException(ErrorCode.INTERNAL_ERROR, "Paylaşım kodu yaradıla bilmədi");
    }
}
