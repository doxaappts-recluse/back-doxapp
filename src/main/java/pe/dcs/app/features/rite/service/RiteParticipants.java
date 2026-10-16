package pe.dcs.app.features.rite.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.person.service.PersonMergeParticipant;
import pe.dcs.app.features.transfer.service.BranchTransferParticipant;
import pe.dcs.app.features.visitor.service.VisitorService;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/** M08 con los demás módulos: traslado de sede, fusión y anonimización de personas y conversión de visitantes. */
public final class RiteParticipants {

    private RiteParticipants() {
    }

    /**
     * Traslado (M21): si se pide mover la membresía, la vigente en la sede origen termina como TRANSFERRED y se abre otra igual (mismo tipo) en la sede destino
     * con origen TRANSFER. Sin esa opción la membresía histórica queda donde está.
     */
    @Component
    @RequiredArgsConstructor
    public static class Transfer implements BranchTransferParticipant {

        private final NamedParameterJdbcTemplate jdbc;
        private final RiteSupport support;
        private final Clock clock;

        @Override
        public String key() {
            return "membership";
        }

        @Override
        public int count(UUID orgId, UUID personId, UUID fromBranchId) {
            Integer n = jdbc.queryForObject("select count(*) from membership where organization_id = :o and person_id = :p and branch_id = :f and current",
                    new MapSqlParameterSource("o", orgId).addValue("p", personId).addValue("f", fromBranchId), Integer.class);
            return n == null ? 0 : n;
        }

        @Override
        public void execute(Context c) {
            if (!c.moveMembership()) {
                return;
            }
            MapSqlParameterSource ps = new MapSqlParameterSource("o", c.orgId()).addValue("p", c.personId()).addValue("f", c.fromBranchId()).addValue("t", c.toBranchId())
                    .addValue("by", c.actorPersonId()).addValue("at", Timestamp.from(clock.instant())).addValue("today", java.sql.Date.valueOf(LocalDate.now(clock.withZone(support.zoneOf(c.fromBranchId())))));
            java.util.List<String> kinds = jdbc.queryForList("select kind from membership where organization_id = :o and person_id = :p and branch_id = :f and current for update", ps, String.class);
            if (kinds.isEmpty()) {
                return;
            }
            jdbc.update("update membership set status = 'ENDED', current = false, end_date = greatest(:today, coalesce(start_date, :today)), exit_reason = 'TRANSFERRED',"
                    + " updated_at = :at, updated_by = :by, version = version + 1 where organization_id = :o and person_id = :p and branch_id = :f and current", ps);
            ps.addValue("id", UUID.randomUUID()).addValue("k", kinds.get(0));
            jdbc.update("insert into membership (id, organization_id, branch_id, person_id, kind, status, current, start_date, origin, requested_by, created_at, created_by)"
                    + " values (:id, :o, :t, :p, :k, 'ACTIVE', true, :today, 'TRANSFER', :by, :at, :by)", ps);
        }
    }

    /** Fusión y anonimización (M06): membresías, ritos, tutores y certificados pasan a la persona que se conserva. */
    @Component
    @RequiredArgsConstructor
    public static class Merge implements PersonMergeParticipant {

        private final NamedParameterJdbcTemplate jdbc;
        private final Clock clock;

        @Override
        public String key() {
            return "rites";
        }

        @Override
        public int count(UUID orgId, UUID sourceId) {
            Integer n = jdbc.queryForObject("select (select count(*) from membership where organization_id = :o and person_id = :s)"
                            + " + (select count(*) from rite where organization_id = :o and (person_id = :s or person2_id = :s or officiant_id = :s or guardian_consent_by = :s))"
                            + " + (select count(*) from rite_guardian where person_id = :s)",
                    new MapSqlParameterSource("o", orgId).addValue("s", sourceId), Integer.class);
            return n == null ? 0 : n;
        }

        @Override
        public String blocker(UUID orgId, UUID sourceId, UUID targetId) {
            MapSqlParameterSource ps = new MapSqlParameterSource("o", orgId).addValue("s", sourceId).addValue("t", targetId);
            if (exists("select 1 from membership a join membership b on b.person_id = :t and b.current where a.person_id = :s and a.current and a.organization_id = :o", ps)) {
                return "error.rite.mergeMembership";
            }
            if (exists("select 1 from rite a join rite b on b.person_id = :t and b.rite_type = 'BAPTISM' and b.status = 'COMPLETED'"
                    + " where a.person_id = :s and a.rite_type = 'BAPTISM' and a.status = 'COMPLETED' and a.organization_id = :o", ps)) {
                return "error.rite.mergeBaptism";
            }
            if (exists("select 1 from rite where organization_id = :o and rite_type = 'MARRIAGE' and ((person_id = :s and person2_id = :t) or (person_id = :t and person2_id = :s))", ps)) {
                return "error.rite.mergeMarriage";
            }
            return null;
        }

        private boolean exists(String sql, MapSqlParameterSource ps) {
            return Boolean.TRUE.equals(jdbc.queryForObject("select exists(" + sql + ")", ps, Boolean.class));
        }

        @Override
        public void migrate(UUID orgId, UUID sourceId, UUID targetId) {
            MapSqlParameterSource ps = new MapSqlParameterSource("o", orgId).addValue("s", sourceId).addValue("t", targetId).addValue("at", Timestamp.from(clock.instant()));
            // Solicitudes abiertas duplicadas: se cancela la de la persona que desaparece.
            jdbc.update("update membership s set status = 'CANCELLED', cancel_reason = 'Fusión de personas', updated_at = :at where s.person_id = :s and s.status = 'PENDING'"
                    + " and exists (select 1 from membership t where t.person_id = :t and t.status = 'PENDING')", ps);
            jdbc.update("update membership set person_id = :t where organization_id = :o and person_id = :s", ps);
            jdbc.update("update rite s set status = 'CANCELLED', cancel_reason = 'Fusión de personas', updated_at = :at where s.organization_id = :o and s.status in ('REQUESTED','APPROVED','SCHEDULED')"
                    + " and exists (select 1 from rite t where t.rite_type = s.rite_type and t.status in ('REQUESTED','APPROVED','SCHEDULED') and t.id <> s.id"
                    + " and (t.person_id = :t or t.person2_id = :t) and (s.person_id = :s or s.person2_id = :s))", ps);
            jdbc.update("update rite set person_id = :t where organization_id = :o and person_id = :s", ps);
            jdbc.update("update rite set person2_id = :t where organization_id = :o and person2_id = :s", ps);
            jdbc.update("update rite set officiant_id = :t where organization_id = :o and officiant_id = :s", ps);
            jdbc.update("update rite set guardian_consent_by = :t where organization_id = :o and guardian_consent_by = :s", ps);
            jdbc.update("delete from rite_guardian s using rite_guardian t where s.person_id = :s and t.person_id = :t and t.rite_id = s.rite_id", ps);
            jdbc.update("update rite_guardian set person_id = :t where person_id = :s", ps);
        }

        /** Borra las notas de salida guardadas; los registros (sin texto libre) se conservan. */
        @Override
        public void anonymize(UUID orgId, UUID personId) {
            jdbc.update("update membership set exit_notes = null where organization_id = :o and person_id = :p", new MapSqlParameterSource("o", orgId).addValue("p", personId));
        }
    }

    /** M07: un caso de visitante solo pasa a CONVERTED si la persona ya tiene una membresía de miembro vigente. */
    @Component
    @RequiredArgsConstructor
    public static class Conversion implements VisitorService.MembershipConversionPort {

        private final NamedParameterJdbcTemplate jdbc;

        @Override
        public boolean hasApprovedMembership(UUID organizationId, UUID personId) {
            return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from membership where organization_id = :o and person_id = :p and current and kind = 'MEMBER')",
                    new MapSqlParameterSource("o", organizationId).addValue("p", personId), Boolean.class));
        }
    }
}
