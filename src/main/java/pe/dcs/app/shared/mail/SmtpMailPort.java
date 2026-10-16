package pe.dcs.app.shared.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.util.Locale;
import java.util.Map;

/**
 * Envío real por SMTP (p.ej. el relay de Brevo), sustituyendo a {@link LoggingMailPort} cuando app.mail.mode=smtp.
 * El asunto y el cuerpo viven en messages_es.properties / messages_en.properties bajo las claves
 * "mail.&lt;templateKey&gt;.subject" y "mail.&lt;templateKey&gt;.body"; los {placeholder} del cuerpo se reemplazan
 * con los params del {@link MailMessage}. Un fallo de envío solo se registra: nunca debe tumbar la operación de
 * negocio que lo originó (alta de acceso, invitación, aviso de vencimiento de contrato).
 */
@Slf4j
@RequiredArgsConstructor
public class SmtpMailPort implements MailPort {

    private final JavaMailSender sender;
    private final MessageSource messages;
    private final String fromAddress;
    private final String fromName;

    @Override
    public void send(MailMessage message) {
        Locale locale = message.locale() == null || message.locale().isBlank() ? new Locale("es") : new Locale(message.locale());
        String subject = resolve(message.templateKey() + ".subject", locale, message.templateKey());
        String body = fill(resolve(message.templateKey() + ".body", locale, ""), message.params());

        try {
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setTo(message.to());
            helper.setFrom(fromAddress, fromName);
            helper.setSubject(subject);
            helper.setText(body, toHtml(body));
            sender.send(mime);
        } catch (Exception e) {
            // Cualquier falla (SMTP, codificación del remitente, red) solo se registra: nunca debe tumbar la
            // operación de negocio que originó el envío.
            log.error("No se pudo enviar el correo to={} template={}: {}", message.to(), message.templateKey(), e.getMessage());
        }
    }

    private String resolve(String key, Locale locale, String fallback) {
        try {
            return messages.getMessage("mail." + key, null, locale);
        } catch (NoSuchMessageException e) {
            log.warn("Falta la plantilla de correo mail.{}", key);
            return fallback;
        }
    }

    private static String fill(String text, Map<String, String> params) {
        String out = text;
        for (Map.Entry<String, String> e : params.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    /** Envoltorio HTML mínimo con la marca, para que el correo no llegue en texto plano desnudo. */
    private static String toHtml(String plainText) {
        String escaped = plainText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
        return "<div style=\"font-family:Arial,sans-serif;max-width:480px;margin:0 auto\">"
                + "<div style=\"background:#27b7f5;padding:16px 24px;border-radius:8px 8px 0 0\">"
                + "<span style=\"color:#ffffff;font-size:18px;font-weight:600\">DoxApp</span></div>"
                + "<div style=\"border:1px solid #e2e8f0;border-top:none;padding:24px;border-radius:0 0 8px 8px;"
                + "color:#0f172a;line-height:1.6;font-size:14px\">" + escaped + "</div></div>";
    }
}
