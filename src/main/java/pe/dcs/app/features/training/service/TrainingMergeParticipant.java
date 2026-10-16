package pe.dcs.app.features.training.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.person.service.PersonMergeParticipant;

import java.util.UUID;

/** M13 · Fusión de personas: traslada matrículas y, si era docente, sus dictados a la persona que se conserva. Sin bloqueos propios. */
@Component
@RequiredArgsConstructor
public class TrainingMergeParticipant implements PersonMergeParticipant {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public String key() {
        return "training";
    }

    @Override
    public int count(UUID orgId, UUID sourceId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("p", sourceId);
        Integer enrollments = jdbc.queryForObject("select count(*) from enrollment where person_id = :p", ps, Integer.class);
        Integer teaching = jdbc.queryForObject("select count(*) from course_class where teacher_person_id = :p", ps, Integer.class);
        return (enrollments == null ? 0 : enrollments) + (teaching == null ? 0 : teaching);
    }

    @Override
    public void migrate(UUID orgId, UUID sourceId, UUID targetId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("s", sourceId).addValue("t", targetId);
        jdbc.update("update course_class set teacher_person_id = :t where teacher_person_id = :s", ps);
        jdbc.update("delete from enrollment where person_id = :s and class_id in (select class_id from enrollment where person_id = :t)", ps);
        jdbc.update("update enrollment set person_id = :t where person_id = :s", ps);
    }
}
