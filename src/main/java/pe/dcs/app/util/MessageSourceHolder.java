package pe.dcs.app.util;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import pe.dcs.app.AppApplication;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Puente estático hacia el {@link MessageSource} de Spring, para que
 * {@link Exceptions} (y cualquier otra clase fuera del contenedor de
 * inyección, como entidades) pueda resolver claves de traducción sin
 * necesitar MessageSource inyectado en cada uno de sus ~500 call-sites.
 */
@Component
public class MessageSourceHolder {

    private static MessageSource messageSource;

    public MessageSourceHolder(MessageSource messageSource) {
        MessageSourceHolder.messageSource = messageSource;
    }

    public static MessageSource get() {
        return messageSource;
    }

    /** Resuelve una clave según el idioma actual (Accept-Language), con fallback a la clave misma. */
    public static String resolve(String key, Object... args) {

        if (key == null || messageSource == null) {
            return key;
        }
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }
}
