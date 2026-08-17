package az.catalog.modules.notification;

import az.catalog.modules.siteconfig.SiteConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Inline HTML e-mail bodies. Deliberately plain: table-free, no external CSS and no
 * remote images, which is what keeps them rendering in Outlook and out of spam folders.
 * Company name and accent colour are read from the published site configuration so a
 * new client installation needs no template edits.
 */
@Component
@RequiredArgsConstructor
public class MailTemplates {

    private final SiteConfigurationService siteConfigurationService;

    public String passwordReset(String name, String link, long ttlHours) {
        return wrap("Şifrə bərpası", """
                <p>Salam, %s.</p>
                <p>Hesabınızın şifrəsini bərpa etmək üçün aşağıdakı düyməyə klikləyin.
                   Keçid <strong>%d saat</strong> ərzində etibarlıdır.</p>
                %s
                <p style="color:#64748b;font-size:13px;">Bu sorğunu siz göndərməmisinizsə, bu məktubu nəzərə almayın.
                   Şifrəniz dəyişməyəcək.</p>
                """.formatted(escape(name), ttlHours, button("Şifrəni bərpa et", link)));
    }

    public String welcome(String name, String siteUrl) {
        return wrap("Xoş gəlmisiniz", """
                <p>Salam, %s.</p>
                <p>Hesabınız uğurla yaradıldı. İndi sifarişlərinizi izləyə, ünvanlarınızı saxlaya
                   və kampaniyalardan xəbərdar ola bilərsiniz.</p>
                %s
                """.formatted(escape(name), button("Sayta keç", siteUrl)));
    }

    public String contactRequest(String fromName, String fromEmail, String subject,
                                 String message, String adminLink) {
        return wrap("Yeni əlaqə sorğusu", """
                <p><strong>Ad:</strong> %s</p>
                <p><strong>E-poçt:</strong> %s</p>
                <p><strong>Mövzu:</strong> %s</p>
                <p><strong>Mesaj:</strong></p>
                <blockquote style="margin:0;padding:12px 16px;background:#f1f5f9;border-left:3px solid #4F46E5;">%s</blockquote>
                %s
                """.formatted(escape(fromName), escape(fromEmail), escape(subject),
                escape(message), button("Panelə keç", adminLink)));
    }

    public String orderConfirmation(String name, String orderNumber, BigDecimal total,
                                    String currency, String orderLink) {
        return wrap("Sifarişiniz qəbul edildi", """
                <p>Salam, %s.</p>
                <p><strong>%s</strong> nömrəli sifarişiniz qəbul edildi.</p>
                <p style="font-size:20px;font-weight:600;">Ümumi məbləğ: %s %s</p>
                <p>Sifarişiniz hazırlandıqca sizə məlumat verəcəyik.</p>
                %s
                """.formatted(escape(name), escape(orderNumber), total.toPlainString(),
                escape(currency), button("Sifarişə bax", orderLink)));
    }

    public String orderStatusChanged(String name, String orderNumber, String statusLabel, String orderLink) {
        return wrap("Sifariş statusu dəyişdi", """
                <p>Salam, %s.</p>
                <p><strong>%s</strong> nömrəli sifarişinizin statusu yeniləndi:</p>
                <p style="font-size:18px;font-weight:600;">%s</p>
                %s
                """.formatted(escape(name), escape(orderNumber), escape(statusLabel),
                button("Sifarişə bax", orderLink)));
    }

    public String paymentReceipt(String name, String orderNumber, BigDecimal amount, String currency) {
        return wrap("Ödəniş qəbul edildi", """
                <p>Salam, %s.</p>
                <p><strong>%s</strong> nömrəli sifariş üçün ödənişiniz uğurla qəbul edildi.</p>
                <p style="font-size:20px;font-weight:600;">Ödənilən məbləğ: %s %s</p>
                <p>Təşəkkür edirik!</p>
                """.formatted(escape(name), escape(orderNumber), amount.toPlainString(), escape(currency)));
    }

    public String adminOrderNotification(String orderNumber, String customerName,
                                         BigDecimal total, String currency, String adminLink) {
        return wrap("Yeni sifariş", """
                <p><strong>Sifariş:</strong> %s</p>
                <p><strong>Müştəri:</strong> %s</p>
                <p><strong>Məbləğ:</strong> %s %s</p>
                %s
                """.formatted(escape(orderNumber), escape(customerName), total.toPlainString(),
                escape(currency), button("Sifarişə bax", adminLink)));
    }

    public String newsletterConfirmation(String confirmLink) {
        return wrap("Abunəliyi təsdiqləyin", """
                <p>Xəbər bülletenimizə abunə olduğunuz üçün təşəkkür edirik.</p>
                <p>Abunəliyi tamamlamaq üçün aşağıdakı düyməyə klikləyin.</p>
                %s
                """.formatted(button("Abunəliyi təsdiqlə", confirmLink)));
    }

    public String backInStock(String productName, String productLink) {
        return wrap("Məhsul yenidən stokdadır", """
                <p><strong>%s</strong> yenidən anbardadır!</p>
                <p>Tükənmədən sifariş edin.</p>
                %s
                """.formatted(escape(productName), button("Məhsula bax", productLink)));
    }

    public String priceDrop(String productName, String productLink) {
        return wrap("Qiymət düşdü", """
                <p>İzlədiyiniz <strong>%s</strong> məhsuluna endirim tətbiq olundu!</p>
                <p>Kampaniya bitməzdən əvvəl sifariş edin.</p>
                %s
                """.formatted(escape(productName), button("Məhsula bax", productLink)));
    }

    public String cartReminder(String name, String firstItemName, int itemCount, String cartLink) {
        String itemsLine = itemCount > 1
                ? "%s və daha %d məhsul".formatted(escape(firstItemName), itemCount - 1)
                : escape(firstItemName);
        return wrap("Səbətinizi unutmusunuz?", """
                <p>Salam, %s.</p>
                <p>Səbətinizdə hələ də <strong>%s</strong> gözləyir — kampaniyalar dəyişə bilər,
                   sifarişinizi indi tamamlamağınızı tövsiyə edirik.</p>
                %s
                """.formatted(escape(name), itemsLine, button("Səbətə qayıt", cartLink)));
    }

    public String referralReward(String name, String promoCode, BigDecimal amount, String siteUrl) {
        return wrap("Dəvət mükafatınız hazırdır", """
                <p>Salam, %s.</p>
                <p>Dəvət proqramı sayəsində <strong>%s ₼</strong> dəyərində promo kodunuz var:</p>
                <p style="font-size:20px;font-weight:700;letter-spacing:1px;">%s</p>
                <p>Bu kodu səbətdə promo kod sahəsinə daxil edərək istifadə edə bilərsiniz.</p>
                %s
                """.formatted(escape(name), amount.toPlainString(), escape(promoCode),
                button("Kataloqa bax", siteUrl + "/kataloq")));
    }

    // -----------------------------------------------------------------------

    private String wrap(String heading, String body) {
        var config = siteConfigurationService.publishedSnapshot();
        String companyName = escape(config.companyName());
        String accent = sanitizeColor(config.primaryColor());

        return """
                <!DOCTYPE html>
                <html lang="az"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title></head>
                <body style="margin:0;padding:0;background:#f1f5f9;
                             font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                  <div style="max-width:560px;margin:0 auto;padding:32px 16px;">
                    <div style="background:#ffffff;border-radius:16px;padding:32px;
                                box-shadow:0 1px 3px rgba(15,23,42,.08);">
                      <div style="font-size:18px;font-weight:700;color:%s;margin-bottom:24px;">%s</div>
                      <h1 style="margin:0 0 20px;font-size:22px;line-height:1.3;color:#0f172a;">%s</h1>
                      <div style="font-size:15px;line-height:1.65;color:#334155;">%s</div>
                    </div>
                    <p style="text-align:center;color:#94a3b8;font-size:12px;margin-top:24px;">
                      %s — bu avtomatik göndərilən məktubdur.
                    </p>
                  </div>
                </body></html>
                """.formatted(escape(heading), accent, companyName, escape(heading), body, companyName);
    }

    private String button(String label, String href) {
        String accent = sanitizeColor(siteConfigurationService.publishedSnapshot().primaryColor());
        return """
                <p style="margin:28px 0;">
                  <a href="%s" style="display:inline-block;background:%s;color:#ffffff;text-decoration:none;
                     padding:13px 26px;border-radius:10px;font-weight:600;font-size:15px;">%s</a>
                </p>
                <p style="color:#94a3b8;font-size:12px;word-break:break-all;">%s</p>
                """.formatted(escapeAttribute(href), accent, escape(label), escape(href));
    }

    /** Configuration is admin-supplied, so it is validated before entering a CSS context. */
    private String sanitizeColor(String color) {
        return color != null && color.matches("#[0-9a-fA-F]{3,8}") ? color : "#4F46E5";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String escapeAttribute(String url) {
        if (url == null) {
            return "#";
        }
        String lower = url.toLowerCase().trim();
        // Never emit a javascript: or data: link from a template.
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return "#";
        }
        return escape(url);
    }
}
