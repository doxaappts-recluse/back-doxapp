package pe.dcs.app.features.pastoral.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.person.service.PersonMergeParticipant;

import java.util.UUID;

/** M12 en la fusión y anonimización de personas (M06): casos pastorales, notas, contactos de seguimiento y peticiones de oración. */
@Component
@RequiredArgsConstructor
public class PastoralMergeParticipant implements PersonMergeParticipant {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public String key() {
        return "pastoralCases";
    }

    @Override
    public int count(UUID orgId, UUID sourceId) {
        Integer n = jdbc.queryForObject("select count(*) from pastoral_case where organization_id = :o and person_id = :s",
                new MapSqlParameterSource("o", orgId).addValue("s", sourceId), Integer.class);
        return n == null ? 0 : n;
    }

    /** Una persona no puede tener dos casos automáticos abiertos a la vez [V8]. */
    @Override
    public String blocker(UUID orgId, UUID sourceId, UUID targetId) {
        Boolean clash = jdbc.queryForObject("""
                select exists (select 1 from pastoral_case a join pastoral_case b on b.person_id = :t
                                where a.person_id = :s and a.organization_id = :o and a.source = 'AUTO_ABSENCE' and b.source = 'AUTO_ABSENCE'
                                  and a.status in ('OPEN','IN_PROGRESS') and b.status in ('OPEN','IN_PROGRESS'))""",
                new MapSqlParameterSource("o", orgId).addValue("s", sourceId).addValue("t", targetId), Boolean.class);
        return Boolean.TRUE.equals(clash) ? "error.pastoral.mergeOpenAutoCases" : null;
    }

    @Override
    public void migrate(UUID orgId, UUID sourceId, UUID targetId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", orgId).addValue("s", sourceId).addValue("t", targetId);
        jdbc.update("update pastoral_case set person_id = :t where organization_id = :o and person_id = :s", ps);
        jdbc.update("update pastoral_case set assigned_to = :t where organization_id = :o and assigned_to = :s", ps);
        jdbc.update("update case_assignment_history set from_person_id = :t where case_id in (select id from pastoral_case where organization_id = :o)"
                + " and from_person_id = :s", ps);
        jdbc.update("update case_assignment_history set to_person_id = :t where case_id in (select id from pastoral_case where organization_id = :o)"
                + " and to_person_id = :s", ps);
        jdbc.update("update case_assignment_history set by_person_id = :t where case_id in (select id from pastoral_case where organization_id = :o)"
                + " and by_person_id = :s", ps);
        jdbc.update("update case_note set author_id = :t where case_id in (select id from pastoral_case where organization_id = :o) and author_id = :s", ps);
        jdbc.update("update follow_up_contact set by_person_id = :t where organization_id = :o and by_person_id = :s and subject_type = 'PASTORAL_CASE'", ps);
        jdbc.update("update follow_up_contact set subject_id = :t where organization_id = :o and subject_type = 'PASTORAL_CASE'"
                + " and subject_id in (select id from pastoral_case where organization_id = :o and person_id = :t)", ps);
        jdbc.update("update prayer_request set requested_by = :t where organization_id = :o and requested_by = :s", ps);
        jdbc.update("update prayer_request set for_person_id = :t where organization_id = :o and for_person_id = :s", ps);
        jdbc.update("update prayer_request set moderated_by = :t where organization_id = :o and moderated_by = :s", ps);
        jdbc.update("update prayer_support set person_id = :t where person_id = :s and request_id in (select id from prayer_request where organization_id = :o)"
                + " on conflict do nothing", ps);
        jdbc.update("delete from prayer_support where person_id = :s and request_id in (select id from prayer_request where organization_id = :o)", ps);
    }

    /** Borra notas y testimonios; los casos abiertos quedan sin identidad de texto pero conservan sus métricas. */
    @Override
    public void anonymize(UUID orgId, UUID personId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", orgId).addValue("p", personId);
        jdbc.update("update case_note set text = null where case_id in (select id from pastoral_case where organization_id = :o and person_id = :p)", ps);
        jdbc.update("update follow_up_contact set notes = null where organization_id = :o and subject_type = 'PASTORAL_CASE'"
                + " and subject_id in (select id from pastoral_case where organization_id = :o and person_id = :p)", ps);
        jdbc.update("update prayer_request set testimony = null, text = '(anonimizado)' where organization_id = :o and requested_by = :p", ps);
    }
}
