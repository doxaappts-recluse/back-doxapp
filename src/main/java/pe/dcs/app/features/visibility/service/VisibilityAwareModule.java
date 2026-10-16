package pe.dcs.app.features.visibility.service;

import java.util.Set;

/**
 * Un módulo con datos de personas que respeta las reglas de visibilidad entre sedes (M21). Cada módulo registra un bean y aplica la
 * regla en su consulta con {@code data_access_rule} y {@code visibility_grant} (M06 lo hace en {@code PersonScope.visibleRead}).
 * Solo estos módulos aparecen en la pantalla de reglas: así una regla nunca promete algo que el módulo no cumple.
 */
public interface VisibilityAwareModule {

    String moduleCode();

    /** Alcances que el módulo sabe aplicar (CURRENT_BRANCH siempre; PERSON_HISTORY, ORGANIZATION, APPROVAL_REQUIRED). */
    Set<String> supportedScopes();
}
