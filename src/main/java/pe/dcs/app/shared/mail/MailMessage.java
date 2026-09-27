package pe.dcs.app.shared.mail;

import java.util.Map;

/** Mensaje transaccional. {@code templateKey} identifica la plantilla es/en (M19); {@code params} son sus variables. */
public record MailMessage(String to, String templateKey, String locale, Map<String, String> params) {
}
