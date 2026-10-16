package pe.dcs.app.shared.mail;

import lombok.extern.slf4j.Slf4j;

/**
 * Modo desarrollo: escribe el correo en el log. OJO: los parámetros incluyen enlaces con token de un solo uso;
 * no usar este modo en producción (se sustituye al implementar M19).
 */
@Slf4j
public class LoggingMailPort implements MailPort {

    @Override
    public void send(MailMessage message) {
        log.warn("[MAIL-DEV] to={} template={} locale={} params={}",
                message.to(), message.templateKey(), message.locale(), message.params());
    }
}
