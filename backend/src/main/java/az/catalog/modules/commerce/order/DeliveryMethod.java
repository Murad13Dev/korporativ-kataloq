package az.catalog.modules.commerce.order;

public enum DeliveryMethod {
    COURIER,
    /** Collect in store — never charges a delivery fee. */
    PICKUP,
    CARGO;

    public String label() {
        return switch (this) {
            case COURIER -> "Kuryerlə çatdırılma";
            case PICKUP -> "Mağazadan götürmə";
            case CARGO -> "Kargo ilə";
        };
    }

    public boolean requiresAddress() {
        return this != PICKUP;
    }
}
