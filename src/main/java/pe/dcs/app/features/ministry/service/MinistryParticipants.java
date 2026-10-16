package pe.dcs.app.features.ministry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.approval.service.ApprovalHandler;
import pe.dcs.app.features.catalog.service.CatalogUsageChecker;
import pe.dcs.app.features.person.service.PersonMergeParticipant;
import pe.dcs.app.features.transfer.service.BranchTransferParticipant;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** M11 con los demás módulos: aprobación de ingresos (M21), traslado de sede, fusión y anonimización de personas (M06) y catálogo de tipos de verificación (M23). */
public final class MinistryParticipants {

    private MinistryParticipants() {
    }

    /** Tipo MINISTRY_JOIN de la bandeja de aprobaciones. Decide con la acción A del módulo de ministerios. */
    @Component
    @RequiredArgsConstructor
    public static class JoinHandler implements ApprovalHandler {

        private final MinistryJoinService service;

        @Override
        public String type() {
            return MinistryJoinService.TYPE;
        }

        @Override
        public String moduleCode() {
            return MinistrySupport.MODULE;
        }

        @Override
        public Map<String, String> notifyParams(ApprovalRow request) {
            return service.notifyParams(request);
        }

        @Override
        public void onApprove(Decision d) {
            service.onApproved(d.request(), d.actorPersonId());
        }
    }

    /** Traslado (M21): con la opción «terminar ministerios» se cierran las asignaciones de la sede origen y se libera el liderazgo que la persona tuviera allí. */
    @Component
    @RequiredArgsConstructor
    public static class Transfer implements BranchTransferParticipant {

        private final NamedParameterJdbcTemplate jdbc;
        private final MinistrySupport support;

        @Override
        public String key() {
            return "ministries";
        }

        @Override
        public int count(UUID orgId, UUID personId, UUID fromBranchId) {
            Integer n = jdbc.queryForObject("select count(*) from ministry_assignment a join branch_ministry b on b.id = a.branch_ministry_id where a.organization_id = :o and a.person_id = :p"
                    + " and a.status = 'ACTIVE' and b.branch_id = :f", new MapSqlParameterSource("o", orgId).addValue("p", personId).addValue("f", fromBranchId), Integer.class);
            return n == null ? 0 : n;
        }

        @Override
        public void execute(Context c) {
            if (!c.endMinistries()) {
                return;
            }
            LocalDate today = support.orgToday(c.orgId());
            MapSqlParameterSource ps = new MapSqlParameterSource("o", c.orgId()).addValue("p", c.personId()).addValue("f", c.fromBranchId()).addValue("t", java.sql.Date.valueOf(today));
            jdbc.update("update ministry_assignment a set status = 'ENDED', to_date = greatest(:t, a.from_date), end_reason = 'Traslado de sede' from branch_ministry b"
                    + " where b.id = a.branch_ministry_id and a.organization_id = :o and a.person_id = :p and a.status = 'ACTIVE' and b.branch_id = :f", ps);
            jdbc.update("update branch_ministry set leader_person_id = null, version = version + 1 where organization_id = :o and branch_id = :f and leader_person_id = :p", ps);
        }
    }

    /** Fusión y anonimización (M06): asignaciones, liderazgo y verificaciones; si ambas personas tenían la misma asignación activa se conserva una, y de cada tipo de verificación la más favorable. */
    @Component
    @RequiredArgsConstructor
    public static class Merge implements PersonMergeParticipant {

        private final NamedParameterJdbcTemplate jdbc;
        private final MinistrySupport support;

        @Override
        public String key() {
            return "ministries";
        }

        @Override
        public int count(UUID orgId, UUID sourceId) {
            Integer n = jdbc.queryForObject("select (select count(*) from ministry_assignment where organization_id = :o and person_id = :s)"
                    + " + (select count(*) from person_screening where organization_id = :o and person_id = :s)"
                    + " + (select count(*) from branch_ministry where organization_id = :o and leader_person_id = :s)", new MapSqlParameterSource("o", orgId).addValue("s", sourceId), Integer.class);
            return n == null ? 0 : n;
        }

        @Override
        public void migrate(UUID orgId, UUID sourceId, UUID targetId) {
            MapSqlParameterSource ps = new MapSqlParameterSource("o", orgId).addValue("s", sourceId).addValue("t", targetId);
            // verificaciones: gana la vigente y, entre dos vigentes, la que vence después (o no vence)
            jdbc.update("update person_screening t set status = s.status, issued_at = s.issued_at, expires_at = s.expires_at, doc_ref = s.doc_ref, notes = s.notes, version = t.version + 1"
                    + " from person_screening s where s.organization_id = :o and s.person_id = :s and t.person_id = :t and s.type = t.type and s.status = 'CLEARED'"
                    + " and (t.status <> 'CLEARED' or (t.expires_at is not null and (s.expires_at is null or s.expires_at > t.expires_at)))", ps);
            jdbc.update("delete from person_screening s using person_screening t where s.organization_id = :o and s.person_id = :s and t.person_id = :t and s.type = t.type", ps);
            jdbc.update("update person_screening set person_id = :t where organization_id = :o and person_id = :s", ps);
            // asignaciones: la misma asignación activa no se duplica
            jdbc.update("delete from ministry_assignment s using ministry_assignment t where s.organization_id = :o and s.person_id = :s and t.person_id = :t and s.branch_ministry_id = t.branch_ministry_id"
                    + " and s.position_id = t.position_id and s.status = 'ACTIVE' and t.status = 'ACTIVE'", ps);
            jdbc.update("update ministry_assignment set person_id = :t where organization_id = :o and person_id = :s", ps);
            jdbc.update("update branch_ministry set leader_person_id = :t where organization_id = :o and leader_person_id = :s", ps);
        }

        @Override
        public void anonymize(UUID orgId, UUID personId) {
            MapSqlParameterSource ps = new MapSqlParameterSource("o", orgId).addValue("p", personId).addValue("t", java.sql.Date.valueOf(support.orgToday(orgId)));
            jdbc.update("delete from person_screening where organization_id = :o and person_id = :p", ps);
            jdbc.update("update ministry_assignment set status = 'ENDED', to_date = greatest(:t, from_date), end_reason = 'Persona anonimizada' where organization_id = :o and person_id = :p and status = 'ACTIVE'", ps);
            jdbc.update("update branch_ministry set leader_person_id = null, version = version + 1 where organization_id = :o and leader_person_id = :p", ps);
        }
    }

    /** M23 [V6]: un tipo de verificación usado por un ministerio o por alguna verificación registrada no se puede borrar. */
    @Component
    @RequiredArgsConstructor
    public static class ScreeningTypeUsage implements CatalogUsageChecker {

        private final NamedParameterJdbcTemplate jdbc;

        @Override
        public boolean inUse(UUID organizationId, String type, String code) {
            if (!"SCREENING_TYPE".equals(type)) {
                return false;
            }
            Integer n = jdbc.queryForObject("select (select count(*) from ministry where organization_id = :o and screening_type = :c)"
                            + " + (select count(*) from person_screening where organization_id = :o and type = :c)", new MapSqlParameterSource("o", organizationId).addValue("c", code), Integer.class);
            return n != null && n > 0;
        }
    }
}
