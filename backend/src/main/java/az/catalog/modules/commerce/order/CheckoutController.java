package az.catalog.modules.commerce.order;

import az.catalog.common.config.RequestLanguage;
import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.modules.commerce.cart.CartOwner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Public · Checkout",
        description = """
                Səbəti sifarişə çevirir. Qonaq və qeydiyyatlı istifadəçi üçün eyni endpoint işləyir.

                Onlayn ödəniş seçildikdə cavabda `paymentUrl` qayıdır — istifadəçi ora yönləndirilir.
                Nağd ödənişdə sifariş dərhal `CONFIRMED` olur.
                """)
@RestController
@RequestMapping("/api/public/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private static final String SESSION_HEADER = "X-Cart-Session";

    private final CheckoutService checkoutService;
    private final OrderService orderService;

    @Operation(summary = "Sifarişi tamamlamaq",
            description = """
                    Qiymətlər səbətdən deyil, **canlı kampaniyalardan** yenidən hesablanır —
                    köhnəlmiş brauzer tabı keçmiş kampaniya qiyməti ilə sifariş verə bilməz.
                    """)
    @PostMapping
    public DataResponse<OrderDtos.CheckoutResponse> checkout(
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionId,
            @Valid @RequestBody OrderDtos.CheckoutRequest request,
            @RequestLanguage String language,
            HttpServletRequest httpRequest) {
        return DataResponse.of(
                checkoutService.checkout(CartOwner.from(sessionId), request, language, httpRequest));
    }

    @Operation(summary = "Sifarişi izləmək (qonaq)",
            description = "Sifariş nömrəsi + e-poçt tələb olunur; yalnız nömrə kifayət deyil.")
    @PostMapping("/track")
    public DataResponse<OrderDtos.OrderDetail> track(@Valid @RequestBody OrderDtos.TrackRequest request) {
        return DataResponse.of(orderService.track(request.orderNumber(), request.email()));
    }
}
