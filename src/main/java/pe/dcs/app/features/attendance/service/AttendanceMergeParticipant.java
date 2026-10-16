package pe.dcs.app.features.attendance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.person.service.PersonMergeParticipant;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

/** M09 en la fusión y anonimización de personas (M06): asistencia, códigos QR usados, check-ins de niños y alertas de inasistencia. */
@Component
@RequiredArgsConstructor
public class AttendanceMergeParticipant implements PersonMergeParticipant {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Override
    public String key() {
        return "attendance";
    }

    @Override
    public int count(UUID orgId, UUID sourceId) {
        Integer n = jdbc.queryForObject("select (select count(*) from attendance_record where organization_id = :o and person_id = :s)"
                        + " + (select count(*) from child_checkin where organization_id = :o and (child_id = :s or guardian_id = :s))",
                new MapSqlParameterSource("o", orgId).addValue("s", sourceId), Integer.class);
        return n == null ? 0 : n;
    }

    /** Si ambas personas figuran en la misma sesión se conserva el registro de la que queda; los niños duplicados en una sesión se descartan. */
    @Override
    public void migrate(UUID orgId, UUID sourceId, UUID targetId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", orgId).addValue("s", sourceId).addValue("t", targetId);
        jdbc.update("delete from attendance_record s using attendance_record t where s.organization_id = :o and s.person_id = :s and t.person_id = :t and t.session_id = s.session_id", ps);
        jdbc.update("update attendance_record set person_id = :t where organization_id = :o and person_id = :s", ps);
        jdbc.update("update attendance_qr_use set person_id = :t where person_id = :s", ps);
        jdbc.update("delete from child_checkin s using child_checkin t where s.organization_id = :o and s.child_id = :s and t.child_id = :t and t.session_id = s.session_id", ps);
        jdbc.update("update child_checkin set child_id = :t where organization_id = :o and child_id = :s", ps);
        jdbc.update("update child_checkin set guardian_id = :t where organization_id = :o and guardian_id = :s", ps);
        jdbc.update("update child_checkin set picked_up_by = :t where organization_id = :o and picked_up_by = :s", ps);
        jdbc.update("delete from attendance_absence_alert where organization_id = :o and person_id = :s", ps);
    }

    /** Borra las alergias guardadas en las etiquetas y cierra sus alertas; los registros de asistencia (sin datos personales) se conservan. */
    @Override
    public void anonymize(UUID orgId, UUID personId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", orgId).addValue("p", personId).addValue("at", Timestamp.from(clock.instant()));
        jdbc.update("update child_checkin set allergy_snapshot = null where organization_id = :o and child_id = :p", ps);
        jdbc.update("update attendance_absence_alert set status = 'RESOLVED', resolved_at = :at where organization_id = :o and person_id = :p and status = 'OPEN'", ps);
    }
}
