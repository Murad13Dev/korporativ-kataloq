package az.catalog.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Every failure the API can return, with its transport status and the default Azerbaijani message.
 * The frontend switches on {@code code}, never on the message text.
 */
@Getter
public enum ErrorCode {

    // --- generic ---------------------------------------------------------
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Göndərilən məlumatlar yanlışdır"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Məlumat tapılmadı"),
    RESOURCE_CONFLICT(HttpStatus.CONFLICT, "Əməliyyat mövcud məlumatla ziddiyyət təşkil edir"),
    ILLEGAL_STATE(HttpStatus.CONFLICT, "Əməliyyat cari vəziyyətdə mümkün deyil"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Daxili sistem xətası"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Fayl həcmi icazə verilən limitdən böyükdür"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Çox sayda sorğu göndərildi. Bir az sonra yenidən cəhd edin"),

    // --- auth ------------------------------------------------------------
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "E-poçt və ya şifrə yanlışdır"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Bu əməliyyat üçün giriş tələb olunur"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Bu əməliyyat üçün icazəniz yoxdur"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Sessiyanın vaxtı bitib"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Token etibarsızdır"),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "Hesab aktiv deyil"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Bu e-poçt artıq istifadə olunur"),
    PASSWORD_TOO_WEAK(HttpStatus.BAD_REQUEST, "Şifrə tələblərə uyğun deyil"),
    CANNOT_MODIFY_SELF(HttpStatus.CONFLICT, "Öz hesabınız üzərində bu əməliyyatı edə bilməzsiniz"),
    LAST_SUPER_ADMIN(HttpStatus.CONFLICT, "Sistemdə ən azı bir SUPER_ADMIN qalmalıdır"),

    // --- media -----------------------------------------------------------
    MEDIA_FILE_IN_USE(HttpStatus.CONFLICT, "Fayl istifadə olunduğu üçün silinə bilməz"),
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "Bu fayl tipi dəstəklənmir"),
    FILE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Fayl saxlanılarkən xəta baş verdi"),

    // --- versioning ------------------------------------------------------
    DRAFT_ALREADY_EXISTS(HttpStatus.CONFLICT, "Artıq açıq draft versiya mövcuddur"),
    NO_DRAFT_TO_PUBLISH(HttpStatus.CONFLICT, "Dərc ediləcək draft versiya yoxdur"),
    VERSION_NOT_PUBLISHABLE(HttpStatus.CONFLICT, "Bu versiya dərc oluna bilməz"),

    // --- catalog ---------------------------------------------------------
    CATEGORY_HAS_ACTIVE_PRODUCTS(HttpStatus.CONFLICT, "Kateqoriyada aktiv məhsullar var"),
    CATEGORY_HAS_CHILDREN(HttpStatus.CONFLICT, "Kateqoriyanın alt kateqoriyaları var"),
    CATEGORY_CYCLE(HttpStatus.BAD_REQUEST, "Kateqoriya öz alt kateqoriyasına köçürülə bilməz"),
    SKU_ALREADY_EXISTS(HttpStatus.CONFLICT, "Bu SKU artıq mövcuddur"),
    SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT, "Bu slug artıq istifadə olunur"),
    ATTRIBUTE_IN_USE(HttpStatus.CONFLICT, "Attribut məhsullarda istifadə olunur"),
    ATTRIBUTE_VALUE_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "Attribut dəyəri tipi uyğun deyil"),
    DEFAULT_LANGUAGE_REQUIRED(HttpStatus.BAD_REQUEST, "Əsas dil üçün tərcümə mütləqdir"),
    LANGUAGE_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "Bu dil aktiv deyil"),
    CANNOT_DEACTIVATE_DEFAULT_LANGUAGE(HttpStatus.CONFLICT, "Əsas dil deaktiv edilə bilməz"),

    // --- discounts -------------------------------------------------------
    DISCOUNT_OVERLAP(HttpStatus.CONFLICT, "Bu tarix aralığında məhsul üçün aktiv endirim mövcuddur"),
    DISCOUNT_INVALID_RANGE(HttpStatus.BAD_REQUEST, "Endirim tarixləri yanlışdır"),
    DISCOUNT_INVALID_VALUE(HttpStatus.BAD_REQUEST, "Endirim dəyəri yanlışdır"),
    PROMO_CODE_INVALID(HttpStatus.BAD_REQUEST, "Promo kod etibarsızdır"),
    PROMO_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "Promo kodun vaxtı bitib"),
    PROMO_CODE_USAGE_LIMIT(HttpStatus.CONFLICT, "Promo kodun istifadə limiti dolub"),
    PROMO_CODE_MIN_SUBTOTAL(HttpStatus.BAD_REQUEST, "Səbət məbləği promo kod üçün kifayət deyil"),

    // --- commerce --------------------------------------------------------
    COMMERCE_DISABLED(HttpStatus.FORBIDDEN, "Onlayn satış bu saytda aktiv deyil"),
    CART_NOT_FOUND(HttpStatus.NOT_FOUND, "Səbət tapılmadı"),
    CART_EMPTY(HttpStatus.BAD_REQUEST, "Səbət boşdur"),
    PRODUCT_NOT_PURCHASABLE(HttpStatus.CONFLICT, "Məhsul satışa açıq deyil"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Anbarda kifayət qədər məhsul yoxdur"),
    ORDER_NOT_CANCELLABLE(HttpStatus.CONFLICT, "Sifariş bu statusda ləğv edilə bilməz"),
    ORDER_STATUS_TRANSITION_INVALID(HttpStatus.CONFLICT, "Sifariş statusu bu ardıcıllıqla dəyişdirilə bilməz"),
    PRICE_CHANGED(HttpStatus.CONFLICT, "Səbətdəki məhsulların qiyməti dəyişib"),

    // --- payment ---------------------------------------------------------
    PAYMENT_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "Ödəniş provayderi ilə əlaqə qurulmadı"),
    PAYMENT_ALREADY_COMPLETED(HttpStatus.CONFLICT, "Ödəniş artıq tamamlanıb"),
    PAYMENT_SIGNATURE_INVALID(HttpStatus.BAD_REQUEST, "Ödəniş imzası etibarsızdır"),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Ödəniş tapılmadı"),
    PAYMENT_WINDOW_EXPIRED(HttpStatus.CONFLICT, "Ödəniş üçün ayrılan vaxt bitib"),

    // --- chat ------------------------------------------------------------
    CONVERSATION_CLOSED(HttpStatus.CONFLICT, "Söhbət bağlanıb"),
    CHAT_DISABLED(HttpStatus.FORBIDDEN, "Canlı dəstək bu saytda aktiv deyil");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
