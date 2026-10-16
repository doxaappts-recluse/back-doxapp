package pe.dcs.app.features.visitor.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.transfer.service.BranchTransferParticipant;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

/**
 * M07 en el traslado de sede (M21): el caso de visitante abierto acompaña a la persona a la sede destino y queda sin responsable
 * (el consolidador era de la sede origen). Si en el destino ya hay un caso abierto de esa persona, el de origen se archiva (motivo «Otro»).
 */
@Component
@RequiredArgsConstructor
public class VisitorTransferParticipant implements BranchTransferParticipant {

    private static final String OPEN = "('NEW', 'IN_FOLLOWUP', 'INTEGRATED')";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Override
    public String key() {
        return "visitorCases";
    }

    @Override
    public int count(UUID orgId, UUID personId, UUID fromBranchId) {
        Integer n = jdbc.queryForObject("select count(*) from visitor_case where organization_id = :o and person_id = :p and branch_id = :f and stage in " + OPEN,
                new MapSqlParameterSource("o", orgId).addValue("p", personId).addValue("f", fromBranchId), Integer.class);
        return n == null ? 0 : n;
    }

    @Override
    public void execute(Context c) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", c.orgId()).addValue("p", c.personId()).addValue("f", c.fromBranchId())
                .addValue("t", c.toBranchId()).addValue("at", Timestamp.from(clock.instant())).addValue("by", c.actorPersonId());
        jdbc.update("update visitor_case set stage = 'ARCHIVED', archive_reason = 'OTHER', closed_at = :at, next_action_date = null, updated_at = :at, updated_by = :by,"
                + " version = version + 1 where organization_id = :o and person_id = :p and branch_id = :f and stage in " + OPEN
                + " and exists (select 1 from visitor_case d where d.person_id = :p and d.branch_id = :t and d.stage in " + OPEN + ")", ps);
        jdbc.update("update visitor_case set branch_id = :t, consolidator_id = null, assigned_at = null, updated_at = :at, updated_by = :by, version = version + 1"
                + " where organization_id = :o and person_id = :p and branch_id = :f and stage in " + OPEN, ps);
    }
}
