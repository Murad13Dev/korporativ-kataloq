package az.catalog.modules.commerce.order;

public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    PAID,
    PARTIALLY_REFUNDED,
    REFUNDED,
    FAILED,
    CANCELLED;

    public String label() {
        return switch (this) {
            case PENDING -> "Gözləyir";
            case AUTHORIZED -> "Bloklanıb";
            case PAID -> "Ödənilib";
            case PARTIALLY_REFUNDED -> "Qismən qaytarılıb";
            case REFUNDED -> "Geri qaytarılıb";
            case FAILED -> "Uğursuz";
            case CANCELLED -> "Ləğv edilib";
        };
    }
}
