package pe.dcs.app.security.authz;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara qué permiso exige un endpoint: {@code @ModuleAccess(module = "PERSON", action = Action.C)}.
 * La verificación es SIEMPRE en el back (rol ∧ nivel ∧ contrato ∧ delegación); el front solo refleja el resultado.
 * Un endpoint de negocio sin esta anotación no es accesible (ver {@link ModuleAccessInterceptor}).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ModuleAccess {

    /** Código del módulo en el catálogo (M03), p. ej. PLATFORM_STAFF. */
    String module();

    Action action() default Action.V;
}
