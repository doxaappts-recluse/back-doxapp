package pe.dcs.app.features.facility.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.facility.dto.FacilityDtos;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M16 · Asignaciones de inventario (persona, ministerio o espacio). [V11] solo un activo disponible (no asignado, ni
 * BROKEN/RETIRED) se asigna; una sola asignación activa por activo (índice único de V27); devolver exige
 * {@code returnedDate >= assignedDate} y actualiza la condición. El job de vencidas vive en {@link FacilityMaintenanceService}.
 */
@Service
@RequiredArgsConstructor
public class InventoryAssignmentService {

    private static final Set<String> TYPES = Set.of("PERSON", "MINISTRY", "SPACE");
    private static final Set<String> CONDITIONS = Set.of("NEW", "GOOD", "FAIR", "POOR", "BROKEN", "RETIRED");
    private static final String ENTITY = "InventoryAssignment";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final FacilitySupport support;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<FacilityDtos.AssignmentView> search(AccessScope scope, FacilityDtos.AssignmentSearch req) {
        FacilityDtos.AssignmentSearch.AssignmentFilters f = req == null || req.filters() == null
                ? new FacilityDtos.AssignmentSearch.AssignmentFilters(null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId());
        StringBuilder w = new StringBuilder("a.organization_id = :org");
        if (!scope.allBranches()) {
            ps.addValue("sb", FacilitySupport.branchIdsOrNone(scope));
            w.append(" and it.branch_id in (:sb)");
        }
        if (f.branchId() != null) {
            w.append(" and it.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (f.itemId() != null) {
            w.append(" and a.item_id = :fi");
            ps.addValue("fi", f.itemId());
        }
        if (FacilitySupport.hasText(f.status())) {
            w.append(" and a.status = :fs");
            ps.addValue("fs", f.status().trim().toUpperCase());
        }
        if (f.assigneePersonId() != null) {
            w.append(" and a.assignee_person_id = :fp");
            ps.addValue("fp", f.assigneePersonId());
        }
        String from = " from inventory_assignment a join inventory_item it on it.id = a.item_id"
                + " left join person p on p.id = a.assignee_person_id left join ministry mi on mi.id = a.assignee_ministry_id"
                + " left join space sp on sp.id = a.assignee_space_id where ";
        Long total = jdbc.queryForObject("select count(*)" + from + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<FacilityDtos.AssignmentView> rows = jdbc.query(SELECT + from + w + " order by a.assigned_date desc limit :lim offset :off", ps, (rs, i) -> view(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    private static final String SELECT = "select a.*, it.name as item_name, trim(p.first_name || ' ' || p.last_name) as person_name, mi.name as ministry_name, sp.name as space_name";

    @Transactional(readOnly = true)
    public FacilityDtos.AssignmentView get(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id).addValue("org", scope.organizationId());
        String vis = scope.allBranches() ? "" : " and it.branch_id in (:sb)";
        if (!scope.allBranches()) {
            ps.addValue("sb", FacilitySupport.branchIdsOrNone(scope));
        }
        return jdbc.query(SELECT + " from inventory_assignment a join inventory_item it on it.id = a.item_id left join person p on p.id = a.assignee_person_id"
                        + " left join ministry mi on mi.id = a.assignee_ministry_id left join space sp on sp.id = a.assignee_space_id"
                        + " where a.id = :id and a.organization_id = :org" + vis, ps, (rs, i) -> view(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public FacilityDtos.AssignmentView assign(AuthenticatedActor actor, AccessScope scope, FacilityDtos.AssignmentRequest r) {
        authz.require(actor, FacilitySupport.MOD_INVENTORY, Action.C);
        if (r == null || r.itemId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "activo");
        }
        List<InventoryItemService.Row> itemRows = jdbc.query("select id, branch_id, code, kind, quantity, status from inventory_item where id = :id for update",
                new MapSqlParameterSource("id", r.itemId()), (rs, i) -> new InventoryItemService.Row((UUID) rs.getObject(1), (UUID) rs.getObject(2),
                        rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getString(6)));
        if (itemRows.isEmpty() || !scope.canSeeBranch(itemRows.get(0).branchId())) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        InventoryItemService.Row item = itemRows.get(0);
        String type = r.assigneeType() == null ? null : r.assigneeType().trim().toUpperCase();
        if (type == null || !TYPES.contains(type)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo de responsable");
        }
        UUID assigneeId = switch (type) {
            case "PERSON" -> r.assigneePersonId();
            case "MINISTRY" -> r.assigneeMinistryId();
            default -> r.assigneeSpaceId();
        };
        if (assigneeId == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "responsable");
        }
        String condition = jdbc.queryForObject("select condition from inventory_item where id = :id", new MapSqlParameterSource("id", r.itemId()), String.class);
        if (Set.of("BROKEN", "RETIRED").contains(String.valueOf(condition)) || !"ACTIVE".equals(item.status())) {
            throw new Exceptions("error.inventory.notAvailable", HttpStatus.UNPROCESSABLE_ENTITY);                          // [V11]
        }
        Integer active = jdbc.queryForObject("select count(*) from inventory_assignment where item_id = :id and status in ('ASSIGNED','OVERDUE')",
                new MapSqlParameterSource("id", r.itemId()), Integer.class);
        if (active != null && active > 0) {
            throw new Exceptions("error.inventory.notAvailable", HttpStatus.UNPROCESSABLE_ENTITY);                          // [V11][M16-T08]
        }
        LocalDate assignedDate = r.assignedDate() == null ? LocalDate.now(support.zoneOf(item.branchId(), scope.organizationId())) : r.assignedDate();
        if (r.expectedReturn() != null && r.expectedReturn().isBefore(assignedDate)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "fecha de devolución esperada");           // [V11]
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into inventory_assignment (id, organization_id, item_id, assignee_type, assignee_person_id, assignee_ministry_id,"
                        + " assignee_space_id, assigned_date, expected_return, status, created_at, created_by) values (:id, :o, :item, :ty, :ap, :am, :as,"
                        + " :ad, :er, 'ASSIGNED', :at, :by)",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("item", r.itemId()).addValue("ty", type)
                        .addValue("ap", "PERSON".equals(type) ? assigneeId : null).addValue("am", "MINISTRY".equals(type) ? assigneeId : null)
                        .addValue("as", "SPACE".equals(type) ? assigneeId : null).addValue("ad", assignedDate).addValue("er", r.expectedReturn())
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()));
        audit.record(new AuditService.Command(FacilitySupport.MOD_INVENTORY, "ASSIGN", ENTITY, id, scope.organizationId(), item.branchId(), Map.of("item", item.code())));
        return get(scope, id);
    }

    @Transactional
    public FacilityDtos.AssignmentView returnItem(AuthenticatedActor actor, AccessScope scope, UUID id, FacilityDtos.AssignmentReturnRequest r) {
        authz.require(actor, FacilitySupport.MOD_INVENTORY, Action.E);
        FacilityDtos.AssignmentView cur = get(scope, id);
        if (!Set.of("ASSIGNED", "OVERDUE").contains(cur.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, cur.status());
        }
        LocalDate returnedDate = r == null || r.returnedDate() == null ? LocalDate.now() : r.returnedDate();
        if (returnedDate.isBefore(cur.assignedDate())) {
            throw new Exceptions("error.inventory.returnDate", HttpStatus.BAD_REQUEST);                                     // [V11]
        }
        String condition = r == null || r.returnCondition() == null ? "GOOD" : r.returnCondition().trim().toUpperCase();
        if (!CONDITIONS.contains(condition)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "condición");
        }
        jdbc.update("update inventory_assignment set status = 'RETURNED', returned_date = :rd, return_condition = :rc, updated_at = :at, updated_by = :by,"
                        + " version = version + 1 where id = :id",
                new MapSqlParameterSource("rd", returnedDate).addValue("rc", condition).addValue("at", Timestamp.from(clock.instant()))
                        .addValue("by", scope.personId()).addValue("id", id));
        jdbc.update("update inventory_item set condition = :c, updated_at = :at, version = version + 1 where id = :item",
                new MapSqlParameterSource("c", condition).addValue("at", Timestamp.from(clock.instant())).addValue("item", cur.itemId()));
        audit.record(new AuditService.Command(FacilitySupport.MOD_INVENTORY, "RETURN", ENTITY, id, scope.organizationId(), null, Map.of("condition", condition)));
        return get(scope, id);
    }

    @Transactional
    public FacilityDtos.AssignmentView markLost(AuthenticatedActor actor, AccessScope scope, UUID id, String note) {
        authz.require(actor, FacilitySupport.MOD_INVENTORY, Action.E);
        FacilityDtos.AssignmentView cur = get(scope, id);
        if (!Set.of("ASSIGNED", "OVERDUE").contains(cur.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, cur.status());
        }
        jdbc.update("update inventory_assignment set status = 'LOST', updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
        jdbc.update("update inventory_item set condition = 'RETIRED', updated_at = :at, version = version + 1 where id = :item",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("item", cur.itemId()));
        audit.record(new AuditService.Command(FacilitySupport.MOD_INVENTORY, "LOST", ENTITY, id, scope.organizationId(), null, Map.of("note", note)));
        return get(scope, id);
    }

    /** [V16] Job: pasa a OVERDUE lo vencido y notifica una vez. */
    @Transactional
    public int markOverdue() {
        List<Map<String, Object>> due = jdbc.queryForList("select a.id, a.organization_id, a.assignee_person_id, it.name as item_name, it.branch_id"
                + " from inventory_assignment a join inventory_item it on it.id = a.item_id"
                + " where a.status = 'ASSIGNED' and a.expected_return is not null and a.expected_return < current_date and a.overdue_alerted_at is null",
                new MapSqlParameterSource());
        int n = 0;
        for (Map<String, Object> d : due) {
            UUID id = (UUID) d.get("id");
            UUID orgId = (UUID) d.get("organization_id");
            UUID personId = (UUID) d.get("assignee_person_id");
            jdbc.update("update inventory_assignment set status = 'OVERDUE', overdue_alerted_at = :at where id = :id and status = 'ASSIGNED'",
                    new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("id", id));
            if (personId != null) {
                notifications.toPersons(NotificationType.INVENTORY_ASSIGNMENT_OVERDUE, orgId, List.of(personId),
                        Map.of("item", String.valueOf(d.get("item_name"))), "/app/inventory/assignments/" + id, "overdue:" + id);
            }
            List<UUID> admins = notifications.branchAdmins(orgId, (UUID) d.get("branch_id"));
            notifications.toPersons(NotificationType.INVENTORY_ASSIGNMENT_OVERDUE, orgId, admins,
                    Map.of("item", String.valueOf(d.get("item_name"))), "/app/inventory/assignments/" + id, "overdue-admin:" + id);
            n++;
        }
        return n;
    }

    private static FacilityDtos.AssignmentView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        String type = rs.getString("assignee_type");
        UUID assigneeId = switch (type) {
            case "PERSON" -> (UUID) rs.getObject("assignee_person_id");
            case "MINISTRY" -> (UUID) rs.getObject("assignee_ministry_id");
            default -> (UUID) rs.getObject("assignee_space_id");
        };
        String label = switch (type) {
            case "PERSON" -> rs.getString("person_name");
            case "MINISTRY" -> rs.getString("ministry_name");
            default -> rs.getString("space_name");
        };
        return new FacilityDtos.AssignmentView((UUID) rs.getObject("id"), (UUID) rs.getObject("item_id"), rs.getString("item_name"), type, assigneeId, label,
                rs.getDate("assigned_date").toLocalDate(), rs.getDate("expected_return") == null ? null : rs.getDate("expected_return").toLocalDate(),
                rs.getDate("returned_date") == null ? null : rs.getDate("returned_date").toLocalDate(), rs.getString("return_condition"), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }
}
