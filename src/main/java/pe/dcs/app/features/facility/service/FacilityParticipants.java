package pe.dcs.app.features.facility.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.approval.service.ApprovalHandler;
import pe.dcs.app.features.catalog.service.CatalogUsageChecker;
import pe.dcs.app.features.person.service.PersonMergeParticipant;

import java.util.Map;
import java.util.UUID;

/** M16 con los demás módulos: aprobación de reservas (M21), catálogo de tipos (M23) y fusión/anonimización de personas (M06). */
public final class FacilityParticipants {

    private FacilityParticipants() {
    }

    /** Tipo SPACE_RESERVATION de la bandeja de aprobaciones. Decide con la acción A de SPACES. */
    @Component
    @RequiredArgsConstructor
    public static class ReservationApprovalHandler implements ApprovalHandler {

        private final ReservationService service;

        @Override
        public String type() {
            return ReservationService.APPROVAL_TYPE;
        }

        @Override
        public String moduleCode() {
            return FacilitySupport.MOD_SPACES;
        }

        @Override
        public Map<String, String> notifyParams(ApprovalRow request) {
            return service.notifyParams(request);
        }

        @Override
        public void onApprove(Decision d) {
            service.onApproved(d.request());
        }

        @Override
        public void onReject(Decision d) {
            service.onRejected(d.request(), d.note());
        }

        @Override
        public void onCancel(Decision d) {
            service.onCancelled(d.request());
        }
    }

    /** M23 [V6]: SPACE_TYPE (espacios) e INVENTORY_CATEGORY (ítems) en uso no se borran del catálogo. */
    @Component
    @RequiredArgsConstructor
    public static class CategoryUsage implements CatalogUsageChecker {

        private final NamedParameterJdbcTemplate jdbc;

        @Override
        public boolean inUse(UUID organizationId, String type, String code) {
            if ("SPACE_TYPE".equals(type)) {
                Integer n = jdbc.queryForObject("select count(*) from space where organization_id = :o and type_code = :c",
                        new MapSqlParameterSource("o", organizationId).addValue("c", code), Integer.class);
                return n != null && n > 0;
            }
            if ("INVENTORY_CATEGORY".equals(type)) {
                Integer n = jdbc.queryForObject("select count(*) from inventory_item where organization_id = :o and category_code = :c",
                        new MapSqlParameterSource("o", organizationId).addValue("c", code), Integer.class);
                return n != null && n > 0;
            }
            return false;
        }
    }

    /** Fusión y anonimización (M06): reservas solicitadas y asignaciones de inventario de la persona. */
    @Component
    @RequiredArgsConstructor
    public static class Merge implements PersonMergeParticipant {

        private final NamedParameterJdbcTemplate jdbc;

        @Override
        public String key() {
            return "facilities";
        }

        @Override
        public int count(UUID orgId, UUID sourceId) {
            Integer n = jdbc.queryForObject("select (select count(*) from reservation where organization_id = :o and requested_by = :s)"
                            + " + (select count(*) from inventory_assignment where organization_id = :o and assignee_person_id = :s)",
                    new MapSqlParameterSource("o", orgId).addValue("s", sourceId), Integer.class);
            return n == null ? 0 : n;
        }

        @Override
        public void migrate(UUID orgId, UUID sourceId, UUID targetId) {
            MapSqlParameterSource ps = new MapSqlParameterSource("o", orgId).addValue("s", sourceId).addValue("t", targetId);
            jdbc.update("update reservation set requested_by = :t where organization_id = :o and requested_by = :s", ps);
            // una asignación activa es única por activo [V11]: si la persona que se conserva ya tiene el mismo activo asignado, se descarta la duplicada
            jdbc.update("delete from inventory_assignment s using inventory_assignment t where s.organization_id = :o and s.assignee_person_id = :s"
                    + " and t.assignee_person_id = :t and t.item_id = s.item_id and s.status in ('ASSIGNED','OVERDUE') and t.status in ('ASSIGNED','OVERDUE')", ps);
            jdbc.update("update inventory_assignment set assignee_person_id = :t where organization_id = :o and assignee_person_id = :s", ps);
        }

        @Override
        public void anonymize(UUID orgId, UUID personId) {
            // las reservas y asignaciones de una persona anonimizada se conservan como agregado (auditoría de uso del espacio/activo);
            // el vínculo con la persona ya no es visible fuera de auditoría porque PersonLookupService deja de resolver su nombre
        }
    }
}
