package pe.dcs.app.features.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.person.service.PersonMergeParticipant;

import java.util.UUID;

/**
 * M14 · Fusión de personas: traslada las inscripciones (y el rol de tutor cuando corresponde) a la persona que se conserva.
 * Si la persona fusionada ya tenía una inscripción activa al mismo evento que la que se conserva, se descarta la duplicada
 * (índice único evento+persona) en vez de bloquear la fusión.
 */
@Component
@RequiredArgsConstructor
public class EventMergeParticipant implements PersonMergeParticipant {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public String key() {
        return "events";
    }

    @Override
    public int count(UUID orgId, UUID sourceId) {
        Integer n = jdbc.queryForObject("select count(*) from event_registration where person_id = :p", new MapSqlParameterSource("p", sourceId), Integer.class);
        return n == null ? 0 : n;
    }

    @Override
    public void migrate(UUID orgId, UUID sourceId, UUID targetId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("s", sourceId).addValue("t", targetId);
        jdbc.update("update event_registration set guardian_person_id = :t where guardian_person_id = :s", ps);
        jdbc.update("delete from event_registration where person_id = :s and status <> 'CANCELLED'"
                + " and event_id in (select event_id from event_registration where person_id = :t and status <> 'CANCELLED')", ps);
        jdbc.update("update event_registration set person_id = :t where person_id = :s", ps);
    }
}
