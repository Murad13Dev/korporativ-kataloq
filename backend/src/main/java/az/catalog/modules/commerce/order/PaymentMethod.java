package az.catalog.modules.commerce.order;

public enum PaymentMethod {
    /** Card payment through the configured provider. */
    ONLINE,
    CASH_ON_DELIVERY,
    BANK_TRANSFER;

    public String label() {
        return switch (this) {
            case ONLINE -> "Onlayn kart ödənişi";
            case CASH_ON_DELIVERY -> "Çatdırılma zamanı nağd";
            case BANK_TRANSFER -> "Bank köçürməsi";
        };
    }

    public boolean requiresOnlinePayment() {
        return this == ONLINE;
    }
}
