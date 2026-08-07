package az.catalog.modules.notification;

import az.catalog.common.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * Transactional e-mail. Every method is {@code @Async} and swallows delivery failures:
 * an SMTP outage must never roll back an order or block a password reset.
 *
 * Sending is disabled by default ({@code app.mail.enabled=false}); the body is then
 * logged instead, which keeps local development free of an SMTP dependency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final AppProperties properties;
    private final MailTemplates templates;

    @Async
    public void sendPasswordReset(String to, String name, String link, long ttlHours) {
        send(to, "Şifrə bərpası", templates.passwordReset(name, link, ttlHours));
    }

    @Async
    public void sendWelcome(String to, String name) {
        send(to, "Xoş gəlmisiniz", templates.welcome(name, properties.getPublicUrl()));
    }

    @Async
    public void sendContactRequestNotification(String subject, String fromName, String fromEmail,
                                               String message, Long requestId) {
        String adminLink = properties.getPublicUrl() + "/admin/contact-requests/" + requestId;
        send(properties.getMail().getAdminNotificationTo(),
                "Yeni əlaqə sorğusu: " + subject,
                templates.contactRequest(fromName, fromEmail, subject, message, adminLink));
    }

    @Async
    public void sendOrderConfirmation(String to, String customerName, String orderNumber,
                                      BigDecimal total, String currency, String orderLink) {
        send(to, "Sifarişiniz qəbul edildi — " + orderNumber,
                templates.orderConfirmation(customerName, orderNumber, total, currency, orderLink));
    }

    @Async
    public void sendOrderStatusChanged(String to, String customerName, String orderNumber,
                                       String statusLabel, String orderLink) {
        send(to, "Sifariş statusu dəyişdi — " + orderNumber,
                templates.orderStatusChanged(customerName, orderNumber, statusLabel, orderLink));
    }

    @Async
    public void sendPaymentReceipt(String to, String customerName, String orderNumber,
                                   BigDecimal amount, String currency) {
        send(to, "Ödəniş qəbul edildi — " + orderNumber,
                templates.paymentReceipt(customerName, orderNumber, amount, currency));
    }

    @Async
    public void sendAdminOrderNotification(String orderNumber, String customerName,
                                           BigDecimal total, String currency, Long orderId) {
        String adminLink = properties.getPublicUrl() + "/admin/orders/" + orderId;
        send(properties.getMail().getAdminNotificationTo(),
                "Yeni sifariş: " + orderNumber,
                templates.adminOrderNotification(orderNumber, customerName, total, currency, adminLink));
    }

    @Async
    public void sendNewsletterConfirmation(String to, String confirmLink) {
        send(to, "Abunəliyi təsdiqləyin", templates.newsletterConfirmation(confirmLink));
    }

    @Async
    public void sendBackInStock(String to, String productName, String productLink) {
        send(to, productName + " yenidən stokdadır", templates.backInStock(productName, productLink));
    }

    private void send(String to, String subject, String htmlBody) {
        if (to == null || to.isBlank()) {
            return;
        }
        if (!properties.getMail().isEnabled()) {
            log.info("[mail disabled] To: {} | Subject: {}", to, subject);
            log.debug("[mail disabled] Body:\n{}", htmlBody);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.getMail().getFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.debug("Sent '{}' to {}", subject, to);
        } catch (Exception ex) {
            // Intentionally not rethrown — see class javadoc.
            log.error("Failed to send '{}' to {}: {}", subject, to, ex.getMessage());
        }
    }
}
