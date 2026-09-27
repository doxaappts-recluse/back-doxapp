package pe.dcs.app.features.ministry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.approval.service.ApprovalEngine;
import pe.dcs.app.features.approval.service.ApprovalHandler;
import pe.dcs.app.features.ministry.dto.MinistryDtos;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M11 · Solicitudes de ingreso a un ministerio de la sede (M21, tipo MINISTRY_JOIN, asunto MINISTRY = la sede del ministerio). [V13] solo a cargos que no son de liderazgo ·
 * [V14] si el ministerio exige verificación y la persona no tiene ninguna, se abre una en estado PENDING; hasta que esté CLEARED y vigente no se puede aprobar ·
 * [V15] decide quien lidera el ministerio (con la acción A) o la administración, nunca quien pidió.
 */
@Service
@RequiredArgsConstructor
public class MinistryJoinService {

    public static final String TYPE = "MINISTRY_JOIN";

    private final NamedParameterJdbcTemplate jdbc;
    private final MinistrySupport support;
    private final MinistryAssignmentService assignments;
    private final ApprovalEngine engine;
    private final AuthorizationService authz;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    @Transactional
    public MinistryDtos.JoinRequestRow request(AuthenticatedActor actor, AccessScope scope, UUID bmId, MinistryDtos.JoinRequest r) {
        authz.require(actor, MinistrySupport.MODULE, Action.C);
        MinistrySupport.BranchMinistryRow bm = support.loadBm(scope, bmId);
        if (r == null || r.personId() == null || r.positionId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona y cargo");
        }
        if (!bm.open()) {
            throw new Exceptions("error.ministry.notActive", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        MinistrySupport.PersonRef p = support.activePerson(scope, r.personId());
        List<Object[]> pos = jdbc.query("select name, is_leader from ministry_position where id = :id and ministry_id = :m", new MapSqlParameterSource("id", r.positionId()).addValue("m", bm.ministryId()),
                (rs, i) -> new Object[]{rs.getString(1), rs.getBoolean(2)});
        if (pos.isEmpty() || (Boolean) pos.get(0)[1]) {
            throw new Exceptions("error.ministry.positionInvalid", HttpStatus.UNPROCESSABLE_ENTITY);                                   // [V13]
        }
        LocalDate today = support.orgToday(bm.orgId());
        if (bm.adultOnly() && p.minor(today)) {
            throw new Exceptions("error.ministry.adultOnly", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        MapSqlParameterSource q = new MapSqlParameterSource("b", bmId).addValue("p", p.id()).addValue("po", r.positionId());
        Integer active = jdbc.queryForObject("select count(*) from ministry_assignment where branch_ministry_id = :b and person_id = :p and position_id = :po and status = 'ACTIVE'", q, Integer.class);
        if (active != null && active > 0) {
            throw new Exceptions("error.ministry.assignmentExists", HttpStatus.CONFLICT);
        }
        Integer open = jdbc.queryForObject("select count(*) from approval_request where type = 'MINISTRY_JOIN' and subject_id = :b and status = 'PENDING' and payload->>'personId' = cast(:p as text)", q, Integer.class);
        if (open != null && open > 0) {
            throw new Exceptions("error.ministry.requestExists", HttpStatus.CONFLICT);
        }
        String state = support.screeningState(p.id(), bm.requiresScreening(), bm.screeningType(), today);
        if ("MISSING".equals(state)) {                                                                                                  // [V14]
            String type = bm.screeningType() == null ? MinistrySupport.DEFAULT_SCREENING : bm.screeningType();
            jdbc.update("insert into person_screening (id, organization_id, person_id, type, status, created_at, created_by) values (:id, :o, :p, :t, 'PENDING', :at, :by)"
                            + " on conflict (person_id, type) do nothing",
                    new MapSqlParameterSource("id", UUID.randomUUID()).addValue("o", bm.orgId()).addValue("p", p.id()).addValue("t", type)
                            .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()));
            state = "PENDING";
        }
        String message = MinistrySupport.trim(r.message(), 300, "mensaje");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("personId", p.id().toString());
        payload.put("personName", p.name());
        payload.put("positionId", r.positionId().toString());
        payload.put("positionName", (String) pos.get(0)[0]);
        payload.put("ministryName", bm.ministryName());
        payload.put("branchName", support.branchName(bm.branchId()));
        UUID id = engine.open(new ApprovalEngine.NewRequest(bm.orgId(), bm.branchId(), null, TYPE, "MINISTRY", bmId, scope.personId(), message, payload));
        if (bm.leaderId() != null && !bm.leaderId().equals(scope.personId())) {
            notifications.toPersons(NotificationType.MINISTRY_JOIN_REQUESTED, bm.orgId(), List.of(bm.leaderId()), Map.of("person", p.name(), "ministry", bm.ministryName(), "branch", support.branchName(bm.branchId())),
                    "/app/ministries", null);
        }
        audit.record(new AuditService.Command(MinistrySupport.MODULE, "JOIN_REQUEST", "BranchMinistry", bmId, bm.orgId(), bm.branchId(), Map.of("person", p.id().toString())));
        return pending(scope, bmId).stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<MinistryDtos.JoinRequestRow> pending(AccessScope scope, UUID bmId) {
        MinistrySupport.BranchMinistryRow bm = support.loadBm(scope, bmId);
        LocalDate today = support.orgToday(bm.orgId());
        return jdbc.query("select a.id, a.payload->>'personId' as pid, a.payload->>'personName' as pname, a.payload->>'positionName' as poname, trim(rq.first_name || ' ' || rq.last_name) as rname,"
                        + " a.reason, a.created_at from approval_request a left join person rq on rq.id = a.requested_by where a.type = 'MINISTRY_JOIN' and a.subject_id = :b and a.status = 'PENDING' order by a.created_at",
                new MapSqlParameterSource("b", bmId), (rs, i) -> {
                    UUID pid = rs.getString("pid") == null ? null : UUID.fromString(rs.getString("pid"));
                    String st = pid == null ? "NOT_REQUIRED" : support.screeningState(pid, bm.requiresScreening(), bm.screeningType(), today);
                    return new MinistryDtos.JoinRequestRow((UUID) rs.getObject("id"), pid, rs.getString("pname"), rs.getString("poname"), rs.getString("rname"), rs.getString("reason"), st,
                            rs.getTimestamp("created_at").toInstant());
                });
    }

    @Transactional
    public void decide(AuthenticatedActor actor, AccessScope scope, UUID bmId, UUID requestId, boolean approve, String note) {
        support.loadBm(scope, bmId);
        Integer n = jdbc.queryForObject("select count(*) from approval_request where id = :r and type = 'MINISTRY_JOIN' and subject_id = :b", new MapSqlParameterSource("r", requestId).addValue("b", bmId), Integer.class);
        if (n == null || n == 0) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if (approve) {
            engine.approve(actor, scope, requestId, note);
        } else {
            engine.reject(actor, scope, requestId, note);
        }
    }

    // ---------------------------------------------------------------- manejador de la aprobación

    void onApproved(ApprovalHandler.ApprovalRow r, UUID actorPersonId) {
        MinistrySupport.BranchMinistryRow bm = support.loadBmRaw(r.subjectId());
        if (actorPersonId != null && !canDecide(actorPersonId, bm)) {
            throw new Exceptions("error.ministry.notLeader", HttpStatus.FORBIDDEN);                                                     // [V15]
        }
        UUID person = UUID.fromString(String.valueOf(r.payload().get("personId")));
        UUID position = UUID.fromString(String.valueOf(r.payload().get("positionId")));
        // aprobar aplica todas las reglas (activo, adulto, verificación vigente…); si alguna falla la decisión no se guarda
        assignments.insert(actorPersonId, null, bm, support.activePersonOrg(bm.orgId(), person), position, null);
    }

    private boolean canDecide(UUID personId, MinistrySupport.BranchMinistryRow bm) {
        if (personId.equals(bm.leaderId())) {
            return true;
        }
        Integer admin = jdbc.queryForObject("select count(*) from user_access where person_id = :p and organization_id = :o and status = 'ACTIVE' and role in ('ORG_ADMIN','ORG_BRANCH_ADMIN')",
                new MapSqlParameterSource("p", personId).addValue("o", bm.orgId()), Integer.class);
        return admin != null && admin > 0;
    }

    Map<String, String> notifyParams(ApprovalHandler.ApprovalRow r) {
        Map<String, String> m = new HashMap<>();
        m.put("person", String.valueOf(r.payload().get("personName")));
        m.put("ministry", String.valueOf(r.payload().get("ministryName")));
        m.put("position", String.valueOf(r.payload().get("positionName")));
        m.put("branch", String.valueOf(r.payload().get("branchName")));
        return m;
    }
}
