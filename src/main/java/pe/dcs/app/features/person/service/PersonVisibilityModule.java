package pe.dcs.app.features.person.service;

import org.springframework.stereotype.Component;
import pe.dcs.app.features.visibility.service.VisibilityAwareModule;

import java.util.Set;

/** M06 en las reglas de visibilidad (M21): la lista, la ficha y el historial de una persona aplican el alcance elegido por la organización (solo lectura). */
@Component
public class PersonVisibilityModule implements VisibilityAwareModule {

    public static final String CODE = "PERSON";

    @Override
    public String moduleCode() {
        return CODE;
    }

    @Override
    public Set<String> supportedScopes() {
        return Set.of("CURRENT_BRANCH", "PERSON_HISTORY", "ORGANIZATION", "APPROVAL_REQUIRED");
    }
}
