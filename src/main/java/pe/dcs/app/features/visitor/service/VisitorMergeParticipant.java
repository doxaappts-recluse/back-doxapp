package pe.dcs.app.features.visitor.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.person.service.PersonMergeParticipant;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

/** M07 en la fusión y anonimización de personas (M06): casos de visitante y contactos de seguimiento. */
@Component
@RequiredArgsConstructor
public class VisitorMergeParticipant implements PersonMergeParticipant {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Override
    public String key() {
        return "visitorCases";
    }

    @Override
    public int count(UUID orgId, UUID sourceId) {
        Integer n = jdbc.queryForObject("select count(*) from visitor_case where organization_id = :o and person_id = :s",
                new MapSqlParameterSource("o", orgId).addValue("s", sourceId), Integer.class);
        return n == null ? 0 : n;
    }

    /** Una persona no puede tener dos casos abiertos en la misma sede [V3]. */
    @Override
    public String blocker(UUID orgId, UUID sourceId, UUID targetId) {
        Boolean clash = jdbc.queryForObject("""
                select exists (select 1 from visitor_case a join visitor_case b on b.branch_id = a.branch_id and b.person_id = :t
                                where a.person_id = :s and a.organization_id = :o
                                  and a.stage in ('NEW', 'IN_FOLLOWUP', 'INTEGRATED') and b.stage in ('NEW', 'IN_FOLLOWUP', 'INTEGRATED'))""",
                new MapSqlParameterSource("o", orgId).addValue("s", sourceId).addValue("t", targetId), Boolean.class);
        return Boolean.TRUE.equals(clash) ? "error.person.mergeOpenCases" : null;
    }

    @Override
    public void migrate(UUID orgId, UUID sourceId, UUID targetId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", orgId).addValue("s", sourceId).addValue("t", targetId);
        jdbc.update("update visitor_case set person_id = :t where organization_id = :o and person_id = :s", ps);
        jdbc.update("update visitor_case set consolidator_id = :t where organization_id = :o and consolidator_id = :s", ps);
        jdbc.update("update visitor_case set invited_by = :t where organization_id = :o and invited_by = :s", ps);
        jdbc.update("update follow_up_contact set by_person_id = :t where organization_id = :o and by_person_id = :s", ps);
        jdbc.update("update follow_up_contact set subject_id = :t where organization_id = :o and subject_type = 'PERSON' and subject_id = :s", ps);
    }

    /** Borra las notas y textos libres; los casos abiertos se archivan (motivo «Otro») para que no queden seguimientos sobre nadie. */
    @Override
    public void anonymize(UUID orgId, UUID personId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", orgId).addValue("p", personId).addValue("at", Timestamp.from(clock.instant()));
        jdbc.update("update follow_up_contact set notes = null where organization_id = :o and ((subject_type = 'PERSON' and subject_id = :p)"
                + " or (subject_type = 'VISITOR_CASE' and subject_id in (select id from visitor_case where organization_id = :o and person_id = :p)))", ps);
        jdbc.update("update visitor_case set stage = 'ARCHIVED', archive_reason = 'OTHER', closed_at = :at, next_action_date = null, updated_at = :at,"
                + " version = version + 1 where organization_id = :o and person_id = :p and stage in ('NEW', 'IN_FOLLOWUP', 'INTEGRATED')", ps);
        jdbc.update("update visitor_case set notes = null where organization_id = :o and person_id = :p", ps);
    }
}
