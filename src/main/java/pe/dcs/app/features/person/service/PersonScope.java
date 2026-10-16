package pe.dcs.app.features.person.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pe.dcs.app.security.authz.AccessScope;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;

/** M06 · Alcance de datos de una persona [V16]: quien no es de todas las sedes solo ve a las personas de sus sedes. */
final class PersonScope {

    static final int ADULT_AGE = 18;

    private PersonScope() {
    }

    /**
     * Condición SQL de visibilidad para la tabla {@code person} con alias {@code a}. Una persona es visible si su sede principal
     * está en el alcance, o si tiene un acceso al sistema en una de esas sedes. Agrega {@code scopeBranches} a los parámetros.
     */
    static String visible(AccessScope scope, MapSqlParameterSource ps, String a) {
        if (scope.allBranches()) {
            return "true";
        }
        List<UUID> ids = scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
        ps.addValue("scopeBranches", ids);
        return "(" + a + ".primary_branch_id in (:scopeBranches) or exists (select 1 from user_access ua where ua.person_id = " + a
                + ".id and ua.branch_id in (:scopeBranches) and ua.status in ('ACTIVE','INVITED')))";
    }

    /**
     * Visibilidad de LECTURA (M21): la de {@link #visible} más lo que abre la regla de visibilidad de la organización para PERSON:
     * ORGANIZATION = todas las personas; PERSON_HISTORY = quienes pasaron por sus sedes; APPROVAL_REQUIRED = quienes tienen una autorización
     * vigente para su sede. La regla se consulta en cada petición, así un cambio o una revocación corta al instante. Nunca sirve para editar.
     */
    static String visibleRead(AccessScope scope, MapSqlParameterSource ps, String a) {
        if (scope.allBranches()) {
            return "true";
        }
        String base = visible(scope, ps, a);
        String rule = "exists (select 1 from data_access_rule r where r.organization_id = " + a + ".organization_id and r.module_code = 'PERSON' and r.enabled and r.scope = ";
        return "(" + base
                + " or " + rule + "'ORGANIZATION')"
                + " or (" + rule + "'PERSON_HISTORY') and exists (select 1 from person_branch pb where pb.person_id = " + a + ".id and pb.branch_id in (:scopeBranches)))"
                + " or (" + rule + "'APPROVAL_REQUIRED') and exists (select 1 from visibility_grant g where g.person_id = " + a
                + ".id and g.module_code = 'PERSON' and g.active and g.visible_until >= current_date and g.target_branch_id in (:scopeBranches))))";
    }

    /** Edad cumplida; null si no hay fecha de nacimiento. */
    static Integer age(LocalDate birth, LocalDate today) {
        return birth == null ? null : Period.between(birth, today).getYears();
    }

    /** Sin fecha de nacimiento se trata como adulto. */
    static boolean minor(LocalDate birth, LocalDate today) {
        Integer age = age(birth, today);
        return age != null && age < ADULT_AGE;
    }
}
