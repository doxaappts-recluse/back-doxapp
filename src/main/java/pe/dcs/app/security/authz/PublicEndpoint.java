package pe.dcs.app.security.authz;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca endpoints de {@code /api/v1} que NO requieren permiso de módulo por diseño (login, cuenta propia, menú).
 * Existe para que "sin @ModuleAccess" sea siempre un error de programación, no un olvido silencioso.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface PublicEndpoint {
}
