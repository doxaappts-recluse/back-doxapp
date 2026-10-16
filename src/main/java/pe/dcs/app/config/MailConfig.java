package pe.dcs.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import pe.dcs.app.shared.mail.LoggingMailPort;
import pe.dcs.app.shared.mail.MailPort;
import pe.dcs.app.shared.mail.SmtpMailPort;

@Configuration
public class MailConfig {

    /** Modo desarrollo (log): se usa salvo que app.mail.mode=smtp. Es el valor por defecto de MAIL_MODE. */
    @Bean
    @ConditionalOnProperty(name = "app.mail.mode", havingValue = "log", matchIfMissing = true)
    public MailPort loggingMailPort() {
        return new LoggingMailPort();
    }

    /** Correo real (M19): relay SMTP configurado en spring.mail.* (p.ej. Brevo). */
    @Bean
    @ConditionalOnProperty(name = "app.mail.mode", havingValue = "smtp")
    public MailPort smtpMailPort(JavaMailSender sender, MessageSource messages,
                                  @Value("${app.mail.from}") String fromAddress,
                                  @Value("${app.mail.from-name}") String fromName) {
        return new SmtpMailPort(sender, messages, fromAddress, fromName);
    }
}
