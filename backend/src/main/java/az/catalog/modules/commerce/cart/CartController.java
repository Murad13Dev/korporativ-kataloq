package az.catalog.modules.commerce.cart;

import az.catalog.common.config.RequestLanguage;
import az.catalog.common.dto.ApiResponses.DataResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Public · Səbət",
        description = """
                Səbət həm qonaq, həm də qeydiyyatlı istifadəçi üçün işləyir.
                Qonaqlar `X-Cart-Session` başlığında brauzerdə saxlanan token göndərir;
                giriş edildikdə qonaq səbəti müştəri səbətinə birləşdirilir.
                """)
@RestController
@RequestMapping("/api/public/cart")
@RequiredArgsConstructor
public class CartController {

    private static final String SESSION_HEADER = "X-Cart-Session";

    private final CartService cartService;

    @Operation(summary = "Səbəti göstərmək",
            description = "Hər çağırışda qiymətlər canlı kampaniyalara görə yenidən hesablanır.")
    @GetMapping
    public DataResponse<CartDtos.CartResponse> get(
            @Parameter(description = "Qonaq səbəti üçün brauzer tokeni")
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionId,
            @RequestLanguage String language) {
        return DataResponse.of(cartService.getCart(CartOwner.from(sessionId), language));
    }

    @Operation(summary = "Səbətdəki məhsul sayı", description = "Header-dəki badge üçün yüngül çağırış.")
    @GetMapping("/count")
    public DataResponse<Map<String, Integer>> count(
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionId) {
        return DataResponse.of(Map.of("count", cartService.itemCount(CartOwner.from(sessionId))));
    }

    @Operation(summary = "Məhsul əlavə etmək")
    @PostMapping("/items")
    public DataResponse<CartDtos.CartResponse> addItem(
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionId,
            @Valid @RequestBody CartDtos.AddItemRequest request,
            @RequestLanguage String language) {
        return DataResponse.of(cartService.addItem(CartOwner.from(sessionId), request, language));
    }

    @Operation(summary = "Sayı dəyişmək", description = "quantity=0 göndərilsə sətir silinir.")
    @PutMapping("/items/{itemId}")
    public DataResponse<CartDtos.CartResponse> updateItem(
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionId,
            @PathVariable Long itemId,
            @Valid @RequestBody CartDtos.UpdateItemRequest request,
            @RequestLanguage String language) {
        return DataResponse.of(cartService.updateItem(CartOwner.from(sessionId), itemId, request, language));
    }

    @Operation(summary = "Sətri silmək")
    @DeleteMapping("/items/{itemId}")
    public DataResponse<CartDtos.CartResponse> removeItem(
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionId,
            @PathVariable Long itemId,
            @RequestLanguage String language) {
        return DataResponse.of(cartService.removeItem(CartOwner.from(sessionId), itemId, language));
    }

    @Operation(summary = "Səbəti boşaltmaq")
    @DeleteMapping
    public DataResponse<CartDtos.CartResponse> clear(
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionId,
            @RequestLanguage String language) {
        return DataResponse.of(cartService.clear(CartOwner.from(sessionId), language));
    }

    @Operation(summary = "Promo kod tətbiq etmək",
            description = "Kampaniya qiymətləri ilə birlikdə işləyir; kodun şərtləri hər oxunuşda yenidən yoxlanılır.")
    @PostMapping("/promo-code")
    public DataResponse<CartDtos.CartResponse> applyPromoCode(
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionId,
            @Valid @RequestBody CartDtos.PromoCodeRequest request,
            @RequestLanguage String language) {
        return DataResponse.of(
                cartService.applyPromoCode(CartOwner.from(sessionId), request.code(), language));
    }

    @Operation(summary = "Promo kodu ləğv etmək")
    @DeleteMapping("/promo-code")
    public DataResponse<CartDtos.CartResponse> removePromoCode(
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionId,
            @RequestLanguage String language) {
        return DataResponse.of(cartService.removePromoCode(CartOwner.from(sessionId), language));
    }
}
