package pe.dcs.app.features.ministry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.ministry.dto.MinistryDtos;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
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
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M11 · Verificación de antecedentes (screening) por persona y tipo, con acción propia Q. [V10] una fila por persona y tipo · [V11] una verificación CLEARED lleva fecha de
 * emisión y, si vence, el vencimiento es posterior a la emisión y a hoy · [V12] el estado EXPIRED es calculado: nadie lo escribe, y una vigente que vence deja de valer ese mismo día.
 * Quien lidera un ministerio (OWN) solo gestiona a las personas de su equipo.
 */
@Service
@RequiredArgsConstructor
public class ScreeningService {

    static final String MODULE = MinistrySupport.MODULE;
    private static final String ENTITY = "PersonScreening";
    private static final Set<String> INPUT = Set.of("NONE", "PENDING", "CLEARED");
    private static final Set<String> FILTER = Set.of("NONE", "PENDING", "CLEARED", "EXPIRED");

    private final NamedParameterJdbcTemplate jdbc;
    private final MinistrySupport support;
    private final AuthorizationService authz;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    private static final String SELECT = "select s.id, s.person_id, trim(p.first_name || ' ' || p.last_name) as pname, s.type, " + MinistrySupport.EFFECTIVE_STATUS + " as eff, s.status as stored,"
            + " s.issued_at, s.expires_at, s.doc_ref, s.notes, s.version,"
            + " (select count(*) from ministry_assignment a join branch_ministry b on b.id = a.branch_ministry_id join ministry m on m.id = b.ministry_id"
            + "  where a.person_id = s.person_id and a.status = 'ACTIVE' and m.requires_screening and (m.screening_type is null or m.screening_type = s.type)) as risk"
            + " from person_screening s join person p on p.id = s.person_id";

    private static MinistryDtos.ScreeningResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MinistryDtos.ScreeningResponse((UUID) rs.getObject("id"), (UUID) rs.getObject("person_id"), rs.getString("pname"), rs.getString("type"), rs.getString("eff"), rs.getString("stored"),
                rs.getDate("issued_at") == null ? null : rs.getDate("issued_at").toLocalDate(), rs.getDate("expires_at") == null ? null : rs.getDate("expires_at").toLocalDate(),
                rs.getString("doc_ref"), rs.getString("notes"), rs.getInt("risk"), rs.getLong("version"));
    }

    /** Personas visibles: las de la organización dentro de las sedes de quien consulta; quien lidera (OWN), solo su equipo activo. Alias: s (screening) y p (persona). */
    private String visible(AccessScope scope, MapSqlParameterSource ps) {
        ps.addValue("org", scope.organizationId());
        StringBuilder sb = new StringBuilder("s.organization_id = :org");
        if (!scope.allBranches()) {
            List<UUID> ids = scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
            ps.addValue("sb", ids);
            sb.append(" and (p.primary_branch_id in (:sb) or exists (select 1 from user_access ua where ua.person_id = p.id and ua.branch_id in (:sb) and ua.status in ('ACTIVE','INVITED')))");
        }
        if (MinistrySupport.isOwn(scope)) {
            ps.addValue("meOwn", scope.personId() == null ? new UUID(0, 0) : scope.personId());
            sb.append(" and exists (select 1 from ministry_assignment a join branch_ministry b on b.id = a.branch_ministry_id where a.person_id = p.id and a.status = 'ACTIVE' and b.leader_person_id = :meOwn)");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<MinistryDtos.ScreeningResponse> search(AuthenticatedActor actor, AccessScope scope, MinistryDtos.ScreeningSearch req) {
        authz.require(actor, MODULE, Action.Q);
        MinistryDtos.ScreeningSearch.Filters f = req == null || req.filters() == null ? new MinistryDtos.ScreeningSearch.Filters(null, null, null, null) : req.filters();
        LocalDate today = support.orgToday(scope.organizationId());
        MapSqlParameterSource ps = new MapSqlParameterSource("today", java.sql.Date.valueOf(today));
        StringBuilder w = new StringBuilder(visible(scope, ps));
        if (MinistrySupport.hasText(f.q())) {
            ps.addValue("q", "%" + f.q().trim().toLowerCase().replace("%", "").replace("_", "") + "%");
            w.append(" and lower(p.first_name || ' ' || p.last_name) like :q");
        }
        if (MinistrySupport.hasText(f.status())) {
            String st = f.status().trim().toUpperCase();
            if (!FILTER.contains(st)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
            }
            w.append(" and ").append(MinistrySupport.EFFECTIVE_STATUS).append(" = :st");
            ps.addValue("st", st);
        }
        if (MinistrySupport.hasText(f.type())) {
            w.append(" and s.type = :ty");
            ps.addValue("ty", f.type().trim().toUpperCase());
        }
        if (f.expiringDays() != null) {
            if (f.expiringDays() < 1 || f.expiringDays() > 365) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "días");
            }
            w.append(" and s.status = 'CLEARED' and s.expires_at is not null and s.expires_at > :today and s.expires_at <= :lim_date");
            ps.addValue("lim_date", java.sql.Date.valueOf(today.plusDays(f.expiringDays())));
        }
        Long total = jdbc.queryForObject("select count(*) from person_screening s join person p on p.id = s.person_id where " + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 50 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<MinistryDtos.ScreeningResponse> rows = jdbc.query(SELECT + " where " + w + " order by lower(p.last_name), lower(p.first_name), s.type limit :lim offset :off", ps, (rs, i) -> map(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    /** Verificaciones de una persona visible (para su ficha y para el formulario de asignación). */
    @Transactional(readOnly = true)
    public List<MinistryDtos.ScreeningResponse> ofPerson(AuthenticatedActor actor, AccessScope scope, UUID personId) {
        authz.require(actor, MODULE, Action.Q);
        MapSqlParameterSource ps = new MapSqlParameterSource("today", java.sql.Date.valueOf(support.orgToday(scope.organizationId()))).addValue("pid", personId);
        String w = visible(scope, ps);
        return jdbc.query(SELECT + " where s.person_id = :pid and " + w + " order by s.type", ps, (rs, i) -> map(rs));
    }

    private MinistryDtos.ScreeningResponse getRaw(UUID id, UUID orgId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("today", java.sql.Date.valueOf(support.orgToday(orgId))).addValue("id", id);
        return jdbc.query(SELECT + " where s.id = :id", ps, (rs, i) -> map(rs)).stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    // ---------------------------------------------------------------- alta y edición

    private record Data(String type, String status, LocalDate issued, LocalDate expires, String docRef, String notes) {
    }

    private Data data(AccessScope scope, MinistryDtos.ScreeningRequest r, LocalDate today, String fixedType) {
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        String type = fixedType != null ? fixedType : MinistrySupport.hasText(r.type()) ? r.type().trim().toUpperCase() : null;
        if (type == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "tipo");
        }
        if (!support.catalogExists(scope.organizationId(), "SCREENING_TYPE", type)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo de verificación");
        }
        String status = MinistrySupport.hasText(r.status()) ? r.status().trim().toUpperCase() : "PENDING";
        if (!INPUT.contains(status)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");                                             // [V12] EXPIRED se calcula
        }
        LocalDate issued = r.issuedAt();
        LocalDate expires = r.expiresAt();
        if ("CLEARED".equals(status)) {
            issued = issued == null ? today : issued;
            if (issued.isAfter(today)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "emisión");
            }
            if (expires != null && (!expires.isAfter(issued) || !expires.isAfter(today))) {
                throw new Exceptions("error.ministry.screeningDates", HttpStatus.UNPROCESSABLE_ENTITY);                                // [V11]
            }
        } else {
            issued = null;
            expires = null;
        }
        return new Data(type, status, issued, expires, MinistrySupport.trim(r.docRef(), 120, "referencia"), MinistrySupport.trim(r.notes(), 500, "notas"));
    }

    private void checkPersonAccess(AccessScope scope, UUID personId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("pid", personId);
        // reutiliza la misma regla de visibilidad que la búsqueda, sobre una fila ficticia de la persona
        String w = visible(scope, ps).replace("s.organization_id", "p.organization_id");
        Integer n = jdbc.queryForObject("select count(*) from person p where p.id = :pid and " + w, ps, Integer.class);
        if (n == null || n == 0) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
    }

    @Transactional
    public MinistryDtos.ScreeningResponse create(AuthenticatedActor actor, AccessScope scope, MinistryDtos.ScreeningRequest r) {
        authz.require(actor, MODULE, Action.Q);
        if (r == null || r.personId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona");
        }
        checkPersonAccess(scope, r.personId());
        MinistrySupport.PersonRef person = support.activePerson(scope, r.personId());
        LocalDate today = support.orgToday(scope.organizationId());
        Data d = data(scope, r, today, null);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into person_screening (id, organization_id, person_id, type, status, issued_at, expires_at, doc_ref, notes, created_at, created_by)"
                            + " values (:id, :o, :p, :t, :s, :i, :e, :d, :n, :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("p", person.id()).addValue("t", d.type()).addValue("s", d.status())
                            .addValue("i", d.issued() == null ? null : java.sql.Date.valueOf(d.issued())).addValue("e", d.expires() == null ? null : java.sql.Date.valueOf(d.expires()))
                            .addValue("d", d.docRef()).addValue("n", d.notes()).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.ministry.screeningExists", HttpStatus.CONFLICT);                                                // [V10]
        }
        audit.record(new AuditService.Command(MODULE, "SCREENING_CREATE", ENTITY, id, scope.organizationId(), null, Map.of("person", person.id().toString(), "type", d.type(), "status", d.status())));
        return getRaw(id, scope.organizationId());
    }

    @Transactional
    public MinistryDtos.ScreeningResponse update(AuthenticatedActor actor, AccessScope scope, UUID id, MinistryDtos.ScreeningRequest r) {
        authz.require(actor, MODULE, Action.Q);
        MapSqlParameterSource vp = new MapSqlParameterSource("today", java.sql.Date.valueOf(support.orgToday(scope.organizationId()))).addValue("id", id);
        String w = visible(scope, vp);
        List<MinistryDtos.ScreeningResponse> cur = jdbc.query(SELECT + " where s.id = :id and " + w, vp, (rs, i) -> map(rs));
        if (cur.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        MinistryDtos.ScreeningResponse before = cur.get(0);
        LocalDate today = support.orgToday(scope.organizationId());
        Data d = data(scope, r, today, before.type());                                                                                   // el tipo no cambia: es parte de la clave
        int n = jdbc.update("update person_screening set status = :s, issued_at = :i, expires_at = :e, doc_ref = :d, notes = :n, updated_at = :at, updated_by = :by, version = version + 1"
                        + " where id = :id and (cast(:v as bigint) is null or version = :v)",
                new MapSqlParameterSource("s", d.status()).addValue("i", d.issued() == null ? null : java.sql.Date.valueOf(d.issued())).addValue("e", d.expires() == null ? null : java.sql.Date.valueOf(d.expires()))
                        .addValue("d", d.docRef()).addValue("n", d.notes()).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("id", id).addValue("v", r.version()));
        if (n == 0) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(MODULE, "SCREENING_UPDATE", ENTITY, id, scope.organizationId(), null, Map.of("person", before.personId().toString(), "status", d.status())));
        if ("CLEARED".equals(before.status()) && !"CLEARED".equals(d.status())) {
            notifyRisk(id);                                                                                                              // pasó de vigente a no vigente
        }
        return getRaw(id, scope.organizationId());
    }

    @Transactional
    public void delete(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, MODULE, Action.Q);
        MapSqlParameterSource vp = new MapSqlParameterSource("today", java.sql.Date.valueOf(support.orgToday(scope.organizationId()))).addValue("id", id);
        String w = visible(scope, vp);
        List<MinistryDtos.ScreeningResponse> cur = jdbc.query(SELECT + " where s.id = :id and " + w, vp, (rs, i) -> map(rs));
        if (cur.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        jdbc.update("delete from person_screening where id = :id", new MapSqlParameterSource("id", id));
        audit.record(new AuditService.Command(MODULE, "SCREENING_DELETE", ENTITY, id, scope.organizationId(), null, Map.of("person", cur.get(0).personId().toString(), "type", cur.get(0).type())));
        if ("CLEARED".equals(cur.get(0).status())) {
            // no queda fila que enlazar: se avisa igual con la persona y el tipo
            notifyPerson(cur.get(0).personId(), cur.get(0).type(), scope.organizationId(), "DELETED:" + id);
        }
    }

    // ---------------------------------------------------------------- avisos de riesgo

    /** Avisa a quien lidera y a la administración de la sede cuando una persona con asignaciones activas en ministerios que exigen verificación deja de tener una vigente. */
    public int notifyRisk(UUID screeningId) {
        List<Object[]> s = jdbc.query("select organization_id, person_id, type from person_screening where id = :id", new MapSqlParameterSource("id", screeningId),
                (rs, i) -> new Object[]{rs.getObject(1), rs.getObject(2), rs.getString(3)});
        if (s.isEmpty()) {
            return 0;
        }
        return notifyPerson((UUID) s.get(0)[1], (String) s.get(0)[2], (UUID) s.get(0)[0], "SCREENING_EXPIRED:" + screeningId);
    }

    private int notifyPerson(UUID personId, String type, UUID orgId, String key) {
        List<Object[]> risks = jdbc.query("select b.id, b.branch_id, b.leader_person_id, m.name from ministry_assignment a join branch_ministry b on b.id = a.branch_ministry_id"
                        + " join ministry m on m.id = b.ministry_id where a.person_id = :p and a.status = 'ACTIVE' and m.requires_screening and (m.screening_type is null or m.screening_type = :t)",
                new MapSqlParameterSource("p", personId).addValue("t", type), (rs, i) -> new Object[]{rs.getObject(1), rs.getObject(2), rs.getObject(3), rs.getString(4)});
        String name = support.personName(personId);
        int sent = 0;
        for (Object[] r : risks) {
            Set<UUID> to = new LinkedHashSet<>(notifications.branchAdmins(orgId, (UUID) r[1]));
            if (r[2] != null) {
                to.add((UUID) r[2]);
            }
            to.remove(personId);
            if (!to.isEmpty()) {
                sent += notifications.toPersons(NotificationType.MINISTRY_SCREENING_EXPIRED, orgId, to, Map.of("person", name, "ministry", String.valueOf(r[3])), "/app/ministries", key + ":" + r[0]);
            }
        }
        return sent;
    }
}
