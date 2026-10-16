package pe.dcs.app.features.group.service;

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

/** M10 con los demás módulos: aprobación de ingresos (M21), traslado de sede, fusión y anonimización de personas (M06) y catálogo de categorías (M23). */
public final class GroupParticipants {

    private GroupParticipants() {
    }

    /** Tipo GROUP_JOIN de la bandeja de aprobaciones. Decide con la acción A del módulo de grupos. */
    @Component
    @RequiredArgsConstructor
    public static class JoinHandler implements ApprovalHandler {

        private final GroupJoinService service;

        @Override
        public String type() {
            return GroupJoinService.TYPE;
        }

        @Override
        public String moduleCode() {
            return GroupSupport.MODULE;
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

    /** Traslado (M21): con la opción «terminar grupos» la persona sale de los grupos de la sede origen; si era el líder de un grupo activo, ese grupo queda en pausa. */
    @Component
    @RequiredArgsConstructor
    public static class Transfer implements BranchTransferParticipant {

        private final NamedParameterJdbcTemplate jdbc;
        private final GroupSupport support;

        @Override
        public String key() {
            return "groups";
        }

        @Override
        public int count(UUID orgId, UUID personId, UUID fromBranchId) {
            Integer n = jdbc.queryForObject("select count(*) from group_member m join small_group g on g.id = m.group_id where g.organization_id = :o and m.person_id = :p and m.status = 'ACTIVE'"
                    + " and g.branch_id = :f", new MapSqlParameterSource("o", orgId).addValue("p", personId).addValue("f", fromBranchId), Integer.class);
            return n == null ? 0 : n;
        }

        @Override
        public void execute(Context c) {
            if (!c.endGroups()) {
                return;
            }
            LocalDate today = support.today(c.fromBranchId());
            MapSqlParameterSource ps = new MapSqlParameterSource("o", c.orgId()).addValue("p", c.personId()).addValue("f", c.fromBranchId()).addValue("t", java.sql.Date.valueOf(today));
            jdbc.update("update small_group g set status = 'PAUSED', status_reason = 'Sin líder por traslado', version = version + 1 where g.organization_id = :o and g.branch_id = :f and g.status = 'ACTIVE'"
                    + " and exists (select 1 from group_member m where m.group_id = g.id and m.person_id = :p and m.status = 'ACTIVE' and m.role = 'LEADER')", ps);
            jdbc.update("update group_member m set status = 'LEFT', left_at = greatest(:t, m.joined_at), left_reason = 'TRANSFER' from small_group g where g.id = m.group_id"
                    + " and g.organization_id = :o and g.branch_id = :f and m.person_id = :p and m.status = 'ACTIVE'", ps);
        }
    }

    /** Fusión y anonimización (M06): integrantes, anfitriones; si ambas personas estaban en el mismo grupo se conserva una sola fila con el rol más alto. */
    @Component
    @RequiredArgsConstructor
    public static class Merge implements PersonMergeParticipant {

        private final NamedParameterJdbcTemplate jdbc;

        @Override
        public String key() {
            return "groups";
        }

        @Override
        public int count(UUID orgId, UUID sourceId) {
            Integer n = jdbc.queryForObject("select (select count(*) from group_member where organization_id = :o and person_id = :s)"
                            + " + (select count(*) from small_group where organization_id = :o and host_person_id = :s)", new MapSqlParameterSource("o", orgId).addValue("s", sourceId), Integer.class);
            return n == null ? 0 : n;
        }

        @Override
        public void migrate(UUID orgId, UUID sourceId, UUID targetId) {
            MapSqlParameterSource ps = new MapSqlParameterSource("o", orgId).addValue("s", sourceId).addValue("t", targetId);
            String rank = "case %s when 'LEADER' then 0 when 'COLEADER' then 1 when 'INTERN' then 2 else 3 end";
            // si la persona que se conserva ya está activa en el mismo grupo, hereda el rol más alto y se descarta la fila duplicada
            jdbc.update("update group_member t set role = s.role from group_member s where t.organization_id = :o and s.organization_id = :o and t.person_id = :t and s.person_id = :s"
                    + " and t.group_id = s.group_id and t.status = 'ACTIVE' and s.status = 'ACTIVE' and " + String.format(rank, "s.role") + " < " + String.format(rank, "t.role"), ps);
            jdbc.update("delete from group_member s using group_member t where s.organization_id = :o and s.person_id = :s and t.person_id = :t and t.group_id = s.group_id"
                    + " and t.status = 'ACTIVE' and s.status = 'ACTIVE'", ps);
            jdbc.update("update group_member set person_id = :t where organization_id = :o and person_id = :s", ps);
            jdbc.update("update small_group set host_person_id = :t where organization_id = :o and host_person_id = :s", ps);
        }

        @Override
        public void anonymize(UUID orgId, UUID personId) {
            jdbc.update("update small_group set host_person_id = null where organization_id = :o and host_person_id = :p", new MapSqlParameterSource("o", orgId).addValue("p", personId));
        }
    }

    /** M23 [V6]: una categoría usada por algún grupo no se puede borrar. */
    @Component
    @RequiredArgsConstructor
    public static class CategoryUsage implements CatalogUsageChecker {

        private final NamedParameterJdbcTemplate jdbc;

        @Override
        public boolean inUse(UUID organizationId, String type, String code) {
            if (!"GROUP_CATEGORY".equals(type)) {
                return false;
            }
            Integer n = jdbc.queryForObject("select count(*) from small_group where organization_id = :o and category = :c", new MapSqlParameterSource("o", organizationId).addValue("c", code), Integer.class);
            return n != null && n > 0;
        }
    }
}
