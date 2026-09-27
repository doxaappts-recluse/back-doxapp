package pe.dcs.app.features.hr.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.approval.service.ApprovalHandler;
import pe.dcs.app.features.person.service.PersonMergeParticipant;

import java.util.Map;
import java.util.UUID;

/** M17 con los demás módulos: aprobación de permisos (M21) y fusión/anonimización de personas (M06). */
public final class HrParticipants {

    private HrParticipants() {
    }

    /** Tipo LEAVE_REQUEST de la bandeja de aprobaciones. Decide con la acción A de HR_LEAVE. */
    @Component
    @RequiredArgsConstructor
    public static class LeaveApprovalHandler implements ApprovalHandler {

        private final LeaveRequestService service;

        @Override
        public String type() {
            return LeaveRequestService.APPROVAL_TYPE;
        }

        @Override
        public String moduleCode() {
            return HrSupport.MOD_LEAVE;
        }

        @Override
        public Map<String, String> notifyParams(ApprovalRow request) {
            return service.notifyParams(request);
        }

        @Override
        public void onApprove(Decision d) {
            service.onApproved(d);
        }

        @Override
        public void onReject(Decision d) {
            service.onRejected(d);
        }

        @Override
        public void onCancel(Decision d) {
            service.onCancelled(d);
        }
    }

    /**
     * Fusión y anonimización (M06). [error.hr.alreadyStaff] si tanto la persona duplicada como la que se conserva tienen ya una
     * ficha no-TERMINATED — mismo índice único que impide dos fichas activas para una sola persona [V1] — la fusión se bloquea en
     * ese caso en vez de intentar dejarlas y violar la restricción.
     */
    @Component
    @RequiredArgsConstructor
    public static class Merge implements PersonMergeParticipant {

        private final NamedParameterJdbcTemplate jdbc;

        @Override
        public String key() {
            return "hr";
        }

        @Override
        public int count(UUID orgId, UUID sourceId) {
            Integer n = jdbc.queryForObject("select count(*) from staff_member where organization_id = :o and person_id = :s",
                    new MapSqlParameterSource("o", orgId).addValue("s", sourceId), Integer.class);
            return n == null ? 0 : n;
        }

        @Override
        public String blocker(UUID orgId, UUID sourceId, UUID targetId) {
            MapSqlParameterSource ps = new MapSqlParameterSource("o", orgId).addValue("s", sourceId).addValue("t", targetId);
            Integer sourceActive = jdbc.queryForObject("select count(*) from staff_member where organization_id = :o and person_id = :s and status <> 'TERMINATED'", ps, Integer.class);
            Integer targetActive = jdbc.queryForObject("select count(*) from staff_member where organization_id = :o and person_id = :t and status <> 'TERMINATED'", ps, Integer.class);
            return sourceActive != null && sourceActive > 0 && targetActive != null && targetActive > 0 ? "error.hr.alreadyStaff" : null;
        }

        @Override
        public void migrate(UUID orgId, UUID sourceId, UUID targetId) {
            jdbc.update("update staff_member set person_id = :t where organization_id = :o and person_id = :s",
                    new MapSqlParameterSource("o", orgId).addValue("s", sourceId).addValue("t", targetId));
        }

        @Override
        public void anonymize(UUID orgId, UUID personId) {
            jdbc.update("update staff_member set bank_encrypted = null where organization_id = :o and person_id = :p",
                    new MapSqlParameterSource("o", orgId).addValue("p", personId));
        }
    }
}
