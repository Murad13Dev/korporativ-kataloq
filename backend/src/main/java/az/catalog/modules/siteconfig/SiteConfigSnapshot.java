package az.catalog.modules.siteconfig;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * Typed read-only view over the parts of the configuration JSON that backend code
 * actually branches on. The document itself stays schemaless so the admin panel can
 * grow new theme knobs without a migration; only the fields with server-side meaning
 * are pinned down here.
 */
public record SiteConfigSnapshot(JsonNode raw) {

    public String companyName() {
        return text("company", "name", "Şirkət");
    }

    public String companyEmail() {
        return text("company", "email", null);
    }

    public String primaryColor() {
        return text("theme", "primaryColor", "#4F46E5");
    }

    public String currency() {
        return text("commerce", "currency", "AZN");
    }

    public String currencySymbol() {
        return text("commerce", "currencySymbol", "₼");
    }

    public BigDecimal freeDeliveryThreshold() {
        return decimal("commerce", "freeDeliveryThreshold", BigDecimal.ZERO);
    }

    public BigDecimal defaultDeliveryPrice() {
        return decimal("commerce", "defaultDeliveryPrice", BigDecimal.ZERO);
    }

    public BigDecimal minOrderTotal() {
        return decimal("commerce", "minOrderTotal", BigDecimal.ZERO);
    }

    // --- feature flags -------------------------------------------------------

    public boolean commerceEnabled() {
        return flag("commerceEnabled", true);
    }

    public boolean cartEnabled() {
        return flag("cartEnabled", true) && commerceEnabled();
    }

    public boolean onlinePaymentEnabled() {
        return flag("onlinePaymentEnabled", true) && commerceEnabled();
    }

    public boolean guestCheckoutEnabled() {
        return flag("guestCheckoutEnabled", true);
    }

    public boolean chatEnabled() {
        return flag("chatEnabled", true);
    }

    public boolean wishlistEnabled() {
        return flag("wishlistEnabled", true);
    }

    public boolean showPricesToGuests() {
        return flag("showPricesToGuests", true);
    }

    public boolean newsletterEnabled() {
        return flag("showNewsletter", true);
    }

    public boolean flag(String name, boolean fallback) {
        JsonNode features = raw == null ? null : raw.get("features");
        if (features == null || !features.hasNonNull(name)) {
            return fallback;
        }
        return features.get(name).asBoolean(fallback);
    }

    // --- helpers -------------------------------------------------------------

    private String text(String section, String field, String fallback) {
        JsonNode node = node(section, field);
        return node == null || node.isNull() ? fallback : node.asText(fallback);
    }

    private BigDecimal decimal(String section, String field, BigDecimal fallback) {
        JsonNode node = node(section, field);
        if (node == null || node.isNull() || !node.isNumber()) {
            return fallback;
        }
        return node.decimalValue();
    }

    private JsonNode node(String section, String field) {
        if (raw == null) {
            return null;
        }
        JsonNode sectionNode = raw.get(section);
        return sectionNode == null ? null : sectionNode.get(field);
    }
}
