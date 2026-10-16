package pe.dcs.app.features.volunteer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.ministry.service.MinistrySupport;
import pe.dcs.app.features.volunteer.dto.VolunteerDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M11b · No disponibilidad de una persona (bloquea la asignación a turnos, salvo override con motivo [V10]). El autoservicio del voluntario (declararla desde el
 * portal) queda para M24; aquí la registra la administración o quien lidera el ministerio.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    static final String MODULE = VolunteerSupport.MODULE;
    private static final Set<String> RECURRENCE = Set.of("ONCE", "WEEKLY");

    private final NamedParameterJdbcTemplate jdbc;
    private final MinistrySupport ministrySupport;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final Clock clock;

    private static final String SELECT = "select a.id, a.person_id, trim(p.first_name || ' ' || p.last_name) as pname, a.from_date, a.to_date, a.recurrence, a.day_of_week,"
            + " a.reason, a.created_at from availability a join person p on p.id = a.person_id";

    private VolunteerDtos.AvailabilityResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new VolunteerDtos.AvailabilityResponse((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3), rs.getDate(4).toLocalDate(),
                rs.getDate(5) == null ? null : rs.getDate(5).toLocalDate(), rs.getString(6), (Integer) rs.getObject(7), rs.getString(8), rs.getTimestamp(9).toInstant());
    }

    @Transactional(readOnly = true)
    public PageResponse<VolunteerDtos.AvailabilityResponse> search(AccessScope scope, VolunteerDtos.AvailabilitySearch req) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", scope.organizationId());
        StringBuilder w = new StringBuilder("a.organization_id = :o");
        if (req != null && req.personId() != null) {
            w.append(" and a.person_id = :p");
            ps.addValue("p", req.personId());
        }
        Long total = jdbc.queryForObject("select count(*) from availability a where " + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 50 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<VolunteerDtos.AvailabilityResponse> rows = jdbc.query(SELECT + " where " + w + " order by a.from_date desc limit :lim offset :off", ps, (rs, i) -> map(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional
    public VolunteerDtos.AvailabilityResponse create(AuthenticatedActor actor, AccessScope scope, VolunteerDtos.AvailabilityRequest r) {
        authz.require(actor, MODULE, Action.C);
        if (r == null || r.personId() == null || r.from() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona y fecha");
        }
        MinistrySupport.PersonRef person = ministrySupport.activePerson(scope, r.personId());
        String rec = r.recurrence() == null || r.recurrence().isBlank() ? "ONCE" : r.recurrence().trim().toUpperCase();
        if (!RECURRENCE.contains(rec)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "recurrencia");
        }
        if (r.to() != null && r.to().isBefore(r.from())) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "hasta");
        }
        Integer dow = r.dayOfWeek();
        if ("WEEKLY".equals(rec) && (dow == null || dow < 1 || dow > 7)) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "día de la semana");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into availability (id, organization_id, person_id, from_date, to_date, recurrence, day_of_week, reason, created_at, created_by)"
                        + " values (:id, :o, :p, :f, :t, :r, :dw, :rs, :at, :by)",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("p", person.id()).addValue("f", java.sql.Date.valueOf(r.from()))
                        .addValue("t", r.to() == null ? null : java.sql.Date.valueOf(r.to())).addValue("r", rec).addValue("dw", "WEEKLY".equals(rec) ? dow : null)
                        .addValue("rs", MinistrySupport.trim(r.reason(), 200, "motivo")).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()));
        audit.record(new AuditService.Command(MODULE, "AVAILABILITY_ADD", "Availability", id, scope.organizationId(), null, Map.of("person", person.id().toString(), "from", r.from().toString())));
        return jdbc.query(SELECT + " where a.id = :id", new MapSqlParameterSource("id", id), (rs, i) -> map(rs)).get(0);
    }

    @Transactional
    public void delete(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, MODULE, Action.D);
        int n = jdbc.update("delete from availability where id = :id and organization_id = :o", new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()));
        if (n == 0) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        audit.record(new AuditService.Command(MODULE, "AVAILABILITY_DELETE", "Availability", id, scope.organizationId(), null, Map.of("id", id.toString())));
    }
}
