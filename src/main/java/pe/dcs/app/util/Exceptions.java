package pe.dcs.app.util;

import lombok.Getter;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;

/**
 * Excepción de negocio con soporte i18n.
 *
 * El primer argumento es la clave de messages_xx.properties (formato error.&lt;dom&gt;.&lt;motivo&gt;).
 * {@link #getCode()} devuelve esa clave y viaja al cliente en el cuerpo de error (ApiError.code);
 * el mensaje ya viene traducido según Accept-Language.
 */
@Getter
public class Exceptions extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public Exceptions(String messageOrKey, HttpStatus status) {
        super(resolve(messageOrKey, null));
        this.status = status;
        this.code = messageOrKey;
    }

    public Exceptions(String key, HttpStatus status, Object... args) {
        super(resolve(key, args));
        this.status = status;
        this.code = key;
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
