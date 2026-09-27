package pe.dcs.app.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.dcs.app.shared.mail.LoggingMailPort;
import pe.dcs.app.shared.mail.MailPort;

@Configuration
public class MailConfig {

    /** Implementación de desarrollo (log). Cualquier otro bean MailPort la reemplaza. */
    @Bean
    @ConditionalOnMissingBean(MailPort.class)
    public MailPort mailPort() {
        return new LoggingMailPort();
    }
}
