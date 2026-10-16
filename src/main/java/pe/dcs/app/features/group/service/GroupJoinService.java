package pe.dcs.app.features.group.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.approval.service.ApprovalEngine;
import pe.dcs.app.features.approval.service.ApprovalHandler;
import pe.dcs.app.features.group.dto.GroupDtos;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M10 · Solicitudes de ingreso a un grupo (M21, tipo GROUP_JOIN, asunto GROUP). [V11] solo si el grupo está activo, abierto y con cupo; decide el líder (con la acción A
 * delegada) o la administración, nunca quien pidió. Aprobar da de alta al integrante con todas las reglas; si alguna falla, la decisión no se aplica.
 */
@Service
@RequiredArgsConstructor
public class GroupJoinService {

    public static final String TYPE = "GROUP_JOIN";

    private final NamedParameterJdbcTemplate jdbc;
    private final GroupSupport support;
    private final GroupService groups;
    private final GroupRulesService rules;
    private final ApprovalEngine engine;
    private final NotificationService notifications;
    private final AuditService audit;

    @Transactional
    public GroupDtos.JoinRequestRow request(AuthenticatedActor actor, AccessScope scope, UUID groupId, GroupDtos.JoinRequest r) {
        GroupSupport.GroupRow g = support.load(scope, groupId);
        if (r == null || r.personId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona");
        }
        if (!"ACTIVE".equals(g.status()) || !g.openToJoin()) {
            throw new Exceptions("error.group.notOpen", HttpStatus.UNPROCESSABLE_ENTITY);                                              // [V11]
        }
        if (g.capacity() != null && support.activeCount(groupId) >= g.capacity()) {
            throw new Exceptions("error.group.full", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        GroupSupport.PersonInfo p = support.activeVisible(scope, r.personId());
        MapSqlParameterSource q = new MapSqlParameterSource("g", groupId).addValue("p", p.id());
        Integer member = jdbc.queryForObject("select count(*) from group_member where group_id = :g and person_id = :p and status = 'ACTIVE'", q, Integer.class);
        if (member != null && member > 0) {
            throw new Exceptions("error.group.alreadyMember", HttpStatus.CONFLICT);
        }
        Integer open = jdbc.queryForObject("select count(*) from approval_request where type = 'GROUP_JOIN' and subject_id = :g and status = 'PENDING' and payload->>'personId' = cast(:p as text)", q, Integer.class);
        if (open != null && open > 0) {
            throw new Exceptions("error.group.requestExists", HttpStatus.CONFLICT);
        }
        if (GroupSupport.minor(p.birthDate(), support.today(g.branchId())) && "ADULT".equals(g.audience())) {
            throw new Exceptions("error.group.minorNotAllowed", HttpStatus.UNPROCESSABLE_ENTITY);                                     // [V7][V14]
        }
        if (!rules.get(g.orgId()).allowMultipleGroups()) {
            Integer other = jdbc.queryForObject("select count(*) from group_member m join small_group x on x.id = m.group_id where m.person_id = :p and m.status = 'ACTIVE' and x.status <> 'CLOSED'", q, Integer.class);
            if (other != null && other > 0) {
                throw new Exceptions("error.group.multipleNotAllowed", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        String message = GroupSupport.trim(r.message(), 300, "mensaje");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("personId", p.id().toString());
        payload.put("personName", p.name());
        payload.put("groupName", g.name());
        payload.put("branchName", support.branchName(g.branchId()));
        UUID id = engine.open(new ApprovalEngine.NewRequest(g.orgId(), g.branchId(), null, TYPE, "GROUP", groupId, scope.personId(), message, payload));
        List<UUID> leaders = jdbc.queryForList("select person_id from group_member where group_id = :g and status = 'ACTIVE' and role in ('LEADER','COLEADER')", q, UUID.class);
        leaders.remove(scope.personId());
        if (!leaders.isEmpty()) {
            notifications.toPersons(NotificationType.GROUP_JOIN_REQUESTED, g.orgId(), leaders, Map.of("person", p.name(), "group", g.name()), "/app/groups/" + groupId, null);
        }
        audit.record(new AuditService.Command(GroupSupport.MODULE, "JOIN_REQUEST", "SmallGroup", groupId, g.orgId(), g.branchId(), Map.of("person", p.id().toString())));
        return pending(scope, groupId).stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<GroupDtos.JoinRequestRow> pending(AccessScope scope, UUID groupId) {
        support.load(scope, groupId);
        return jdbc.query("select a.id, (a.payload->>'personId') as pid, a.payload->>'personName' as pname, trim(rq.first_name || ' ' || rq.last_name) as rname, a.reason, a.created_at"
                        + " from approval_request a left join person rq on rq.id = a.requested_by where a.type = 'GROUP_JOIN' and a.subject_id = :g and a.status = 'PENDING' order by a.created_at",
                new MapSqlParameterSource("g", groupId), (rs, i) -> new GroupDtos.JoinRequestRow((UUID) rs.getObject("id"), rs.getString("pid") == null ? null : UUID.fromString(rs.getString("pid")),
                        rs.getString("pname"), rs.getString("rname"), rs.getString("reason"), rs.getTimestamp("created_at").toInstant()));
    }

    /** Aprobar o rechazar desde la ficha del grupo: el motor comprueba la acción A, el alcance y que no sea quien pidió. */
    @Transactional
    public void decide(AuthenticatedActor actor, AccessScope scope, UUID groupId, UUID requestId, boolean approve, String note) {
        support.load(scope, groupId);
        Integer n = jdbc.queryForObject("select count(*) from approval_request where id = :r and type = 'GROUP_JOIN' and subject_id = :g", new MapSqlParameterSource("r", requestId).addValue("g", groupId), Integer.class);
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
        GroupSupport.GroupRow g = support.loadRaw(r.subjectId());
        if (actorPersonId != null && !canDecide(actorPersonId, g)) {
            throw new Exceptions("error.group.notLeader", HttpStatus.FORBIDDEN);                                                     // [V13]
        }
        if (!"ACTIVE".equals(g.status()) || !g.openToJoin()) {
            throw new Exceptions("error.group.notOpen", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        UUID person = UUID.fromString(String.valueOf(r.payload().get("personId")));
        groups.addChecked(g, support.person(g.orgId(), person), "MEMBER", actorPersonId, null);
    }

    /** Administración de la organización o de la sede, o líder / co-líder del grupo. */
    private boolean canDecide(UUID personId, GroupSupport.GroupRow g) {
        Integer admin = jdbc.queryForObject("select count(*) from user_access where person_id = :p and organization_id = :o and status = 'ACTIVE' and role in ('ORG_ADMIN','ORG_BRANCH_ADMIN')",
                new MapSqlParameterSource("p", personId).addValue("o", g.orgId()), Integer.class);
        if (admin != null && admin > 0) {
            return true;
        }
        Integer lead = jdbc.queryForObject("select count(*) from group_member where group_id = :g and person_id = :p and status = 'ACTIVE' and role in ('LEADER','COLEADER')",
                new MapSqlParameterSource("g", g.id()).addValue("p", personId), Integer.class);
        return lead != null && lead > 0;
    }

    Map<String, String> notifyParams(ApprovalHandler.ApprovalRow r) {
        Map<String, String> m = new HashMap<>();
        m.put("person", String.valueOf(r.payload().get("personName")));
        m.put("group", String.valueOf(r.payload().get("groupName")));
        m.put("branch", String.valueOf(r.payload().get("branchName")));
        return m;
    }
}
