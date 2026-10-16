package pe.dcs.app.shared.mail;

/**
 * Puerto de salida de correo. Hasta que exista el canal real (M19 / NotificationService) la implementación por
 * defecto solo registra el mensaje en el log (modo desarrollo). Sustituir la implementación no toca el dominio.
 */
public interface MailPort {

    void send(MailMessage message);
}
