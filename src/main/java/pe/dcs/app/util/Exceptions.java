package pe.dcs.app.util;

import lombok.Getter;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;

/**
 * Excepción de negocio con soporte i18n.
 *
 * El primer argumento puede ser:
 *  - una clave de messages_xx.properties (recomendado, prefijo "error."), o
 *  - un mensaje literal (comportamiento legado).
 *
 * Si la clave no existe en el bundle, se usa la clave/mensaje tal cual
 * como fallback (ver {@link #resolve}), así que no rompe nada que no
 * haya sido migrado todavía a claves — la migración es incremental.
 */
@Getter
public class Exceptions extends RuntimeException {

    private final HttpStatus status;

    public Exceptions(String messageOrKey, HttpStatus status) {
        super(resolve(messageOrKey, null));
        this.status = status;
    }

    public Exceptions(String key, HttpStatus status, Object... args) {
        super(resolve(key, args));
        this.status = status;
    }

    private static String resolve(String key, Object[] args) {

        if (key == null) {
            return null;
        }

        MessageSource messageSource = MessageSourceHolder.get();

        if (messageSource == null) {
            return key;
        }

        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }
}
