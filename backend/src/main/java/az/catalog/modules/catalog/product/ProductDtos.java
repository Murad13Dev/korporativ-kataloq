package az.catalog.modules.catalog.product;

import az.catalog.common.domain.ContentStatus;
import az.catalog.modules.catalog.discount.PriceInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class ProductDtos {

    private ProductDtos() {
    }

    // -----------------------------------------------------------------------
    // Storefront
    // -----------------------------------------------------------------------

    /**
     * Grid card. {@code price} carries the discount ribbon data, so the frontend renders
     * the strike-through, the percentage badge and the countdown without a second call.
     */
    public record ProductCard(
            Long id,
            String slug,
            String sku,
            String name,
            String shortDescription,
            String imageUrl,
            String imageAlt,
            Long categoryId,
            String categorySlug,
            String categoryName,
            PriceInfo price,
            boolean showPrice,
            boolean featured,
            boolean isNew,
            boolean purchasable,
            StockStatus stockStatus,
            Integer availableQuantity
    ) {
    }

    public record ProductDetail(
            Long id,
            String slug,
            String sku,
            String barcode,
            String name,
            String shortDescription,
            String description,
            String seoTitle,
            String seoDescription,
            Long categoryId,
            String categorySlug,
            String categoryName,
            PriceInfo price,
            boolean showPrice,
            String currency,
            BigDecimal compareAtPrice,
            boolean featured,
            boolean isNew,
            boolean purchasable,
            StockStatus stockStatus,
            Integer availableQuantity,
            int minOrderQuantity,
            Integer maxOrderQuantity,
            Integer weightGrams,
            List<ImageDto> images,
            List<DocumentDto> documents,
            List<SpecificationGroup> specifications,
            OffsetDateTime publishedAt,
            List<VariantDto> variants,
            List<VariantAttributeDto> variantAttributes,
            double averageRating,
            long reviewCount
    ) {
    }

    public record ImageDto(Long id, Long mediaId, String url, String alt, boolean main, int sortOrder) {
    }

    /** One purchasable colour/size (etc.) combination, as seen by a shopper. */
    public record VariantDto(
            Long id,
            String sku,
            BigDecimal price,
            boolean priceOverridden,
            StockStatus stockStatus,
            Integer availableQuantity,
            String imageUrl,
            boolean isDefault,
            List<Long> optionIds
    ) {
    }

    /** One variation axis ("Rəng") and the option chips a shopper can pick for it. */
    public record VariantAttributeDto(
            Long attributeId,
            String code,
            String name,
            List<VariantOptionDto> options
    ) {
    }

    public record VariantOptionDto(Long id, String value, String label, String colorHex) {
    }

    public record DocumentDto(Long id, Long mediaId, String url, String title,
                              ProductDocument.DocumentType documentType, long fileSize) {
    }

    /** Specifications grouped the way the admin organised the attributes. */
    public record SpecificationGroup(String groupCode, String groupName, List<SpecificationLine> lines) {
    }

    public record SpecificationLine(String attributeCode, String label, String value, String unit) {
    }

    // -----------------------------------------------------------------------
    // Admin
    // -----------------------------------------------------------------------

    public record ProductRow(
            Long id,
            String sku,
            String slug,
            String name,
            String imageUrl,
            Long categoryId,
            String categoryName,
            BigDecimal price,
            PriceInfo effectivePrice,
            String currency,
            ContentStatus status,
            boolean featured,
            boolean purchasable,
            boolean trackStock,
            int stockQuantity,
            StockStatus stockStatus,
            int sortOrder,
            long viewCount,
            OffsetDateTime updatedAt
    ) {
    }

    public record ProductResponse(
            Long id,
            String sku,
            String barcode,
            String slug,
            Long categoryId,
            BigDecimal price,
            BigDecimal compareAtPrice,
            BigDecimal costPrice,
            String currency,
            BigDecimal taxRate,
            boolean showPrice,
            boolean purchasable,
            boolean trackStock,
            int stockQuantity,
            int lowStockThreshold,
            boolean allowBackorder,
            int minOrderQuantity,
            Integer maxOrderQuantity,
            Integer weightGrams,
            boolean featured,
            boolean isNew,
            ContentStatus status,
            int sortOrder,
            long viewCount,
            OffsetDateTime publishedAt,
            PriceInfo effectivePrice,
            Map<String, TranslationDto> translations,
            List<ImageDto> images,
            List<DocumentDto> documents,
            List<AttributeValueDto> attributes,
            List<Long> relatedProductIds
    ) {
    }

    public record TranslationDto(
            @NotBlank @Size(max = 255) String name,
            String shortDescription,
            String description,
            @Size(max = 255) String seoTitle,
            @Size(max = 500) String seoDescription
    ) {
    }

    public record AttributeValueDto(
            @NotNull Long attributeId,
            String attributeCode,
            String attributeName,
            Long optionId,
            String textValue,
            BigDecimal numberValue,
            Boolean booleanValue
    ) {
    }

    public record SaveRequest(
            @NotBlank @Size(max = 80) String sku,
            @Size(max = 80) String barcode,
            @Size(max = 180) String slug,
            Long categoryId,

            @DecimalMin(value = "0.00") BigDecimal price,
            @DecimalMin(value = "0.00") BigDecimal compareAtPrice,
            @DecimalMin(value = "0.00") BigDecimal costPrice,
            @Size(max = 8) String currency,
            @DecimalMin(value = "0.00") BigDecimal taxRate,
            Boolean showPrice,

            Boolean purchasable,
            Boolean trackStock,
            @Min(0) Integer stockQuantity,
            @Min(0) Integer lowStockThreshold,
            Boolean allowBackorder,
            @Min(1) Integer minOrderQuantity,
            @Min(1) Integer maxOrderQuantity,
            @Min(0) Integer weightGrams,

            Boolean featured,
            Boolean isNew,
            ContentStatus status,
            Integer sortOrder,

            @NotEmpty(message = "Ən azı bir dil üçün tərcümə tələb olunur")
            Map<String, @Valid TranslationDto> translations,

            List<@Valid AttributeValueDto> attributes,
            List<Long> relatedProductIds
    ) {
    }

    public record StatusRequest(@NotNull ContentStatus status) {
    }

    public record FeaturedRequest(@NotNull Boolean featured) {
    }

    public record StockRequest(@NotNull @Min(0) Integer stockQuantity) {
    }

    // --- images ---

    public record AddImageRequest(@NotNull Long mediaId, Boolean main, Integer sortOrder) {
    }

    public record UpdateImageRequest(Integer sortOrder) {
    }

    public record ReorderRequest(@NotEmpty List<@Valid ReorderItem> items) {
    }

    public record ReorderItem(@NotNull Long id, @NotNull Integer sortOrder) {
    }

    // --- documents ---

    public record AddDocumentRequest(
            @NotNull Long mediaId,
            @NotBlank @Size(max = 255) String title,
            ProductDocument.DocumentType documentType,
            Integer sortOrder
    ) {
    }

    // --- variants ---

    public record VariantAdminDto(
            Long id,
            String sku,
            BigDecimal priceOverride,
            boolean trackStock,
            int stockQuantity,
            boolean allowBackorder,
            Long imageMediaId,
            String imageUrl,
            int sortOrder,
            boolean isDefault,
            boolean active,
            List<Long> optionIds,
            String optionLabel
    ) {
    }

    public record SaveVariantRequest(
            @NotBlank @Size(max = 80) String sku,
            @DecimalMin(value = "0.00") BigDecimal priceOverride,
            Boolean trackStock,
            @Min(0) Integer stockQuantity,
            Boolean allowBackorder,
            Long imageMediaId,
            Integer sortOrder,
            Boolean isDefault,
            Boolean active,
            @NotEmpty(message = "Ən azı bir seçim tələb olunur") List<Long> optionIds
    ) {
    }

    // --- product inquiry (public) ---

    // --- bundles ("frequently bought together") ---

    public record BundleDto(
            Long id,
            String name,
            List<ProductCard> items,
            BigDecimal totalPrice,
            String currency
    ) {
    }

    public record BundleAdminDto(
            Long id,
            String name,
            Long primaryProductId,
            String primaryProductName,
            List<BundleItemDto> items,
            boolean active,
            int sortOrder
    ) {
    }

    public record BundleItemDto(Long productId, String productName, String productSku) {
    }

    public record SaveBundleRequest(
            @NotBlank @Size(max = 160) String name,
            @NotNull Long primaryProductId,
            @NotEmpty List<Long> itemProductIds,
            Boolean active,
            Integer sortOrder
    ) {
    }

    public record StockNotificationRequest(
            @NotBlank @jakarta.validation.constraints.Email @Size(max = 190) String email
    ) {
    }

    public record InquiryRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 160) String companyName,
            @NotBlank @jakarta.validation.constraints.Email @Size(max = 190) String email,
            @Size(max = 40) String phone,
            @NotBlank @Size(max = 4000) String message,
            @jakarta.validation.constraints.AssertTrue(message = "Məxfilik siyasəti qəbul edilməlidir")
            boolean privacyAccepted
    ) {
    }
}
