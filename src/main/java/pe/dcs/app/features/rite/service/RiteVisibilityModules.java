package pe.dcs.app.features.rite.service;

import org.springframework.stereotype.Component;
import pe.dcs.app.features.visibility.service.VisibilityAwareModule;

import java.util.Set;

/** M08 en las reglas de visibilidad (M21): la lista y el historial de membresía y de cada rito aplican el alcance elegido por la organización (solo lectura). */
public final class RiteVisibilityModules {

    private static final Set<String> SCOPES = Set.of("CURRENT_BRANCH", "PERSON_HISTORY", "ORGANIZATION", "APPROVAL_REQUIRED");

    private RiteVisibilityModules() {
    }

    abstract static class Base implements VisibilityAwareModule {
        @Override
        public Set<String> supportedScopes() {
            return SCOPES;
        }
    }

    @Component
    public static class Membership extends Base {
        @Override
        public String moduleCode() {
            return "MEMBERSHIP";
        }
    }

    @Component
    public static class Baptism extends Base {
        @Override
        public String moduleCode() {
            return "BAPTISM";
        }
    }

    @Component
    public static class Marriage extends Base {
        @Override
        public String moduleCode() {
            return "MARRIAGE";
        }
    }

    @Component
    public static class Dedication extends Base {
        @Override
        public String moduleCode() {
            return "CHILD_DEDICATION";
        }
    }
}
