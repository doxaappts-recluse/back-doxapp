package pe.dcs.app.features.hr.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.approval.service.ApprovalEngine;
import pe.dcs.app.features.approval.service.ApprovalHandler;
import pe.dcs.app.features.hr.dto.HrDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M17 · Vacaciones y permisos (HR_LEAVE). [V10] sin solape con APPROVED; [V11] VACATION ≤ saldo disponible del año (entitled -
 * taken - pending); [V12] fecha de inicio ≥ hoy salvo SICK_LEAVE (retroactiva con adjunto); [V13] SICK_LEAVE &gt; 3 días exige
 * adjunto; [V14] quien solicita no aprueba (lo aplica {@link ApprovalEngine#approve}, no hace falta repetirlo aquí).
 */
@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    public static final String APPROVAL_TYPE = "LEAVE_REQUEST";
    private static final Set<String> TYPES = Set.of("VACATION", "PERSONAL_PERMIT", "SICK_LEAVE", "MATERNITY_PATERNITY", "UNPAID_LEAVE", "OTHER");
    private static final String ENTITY = "LeaveRequest";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final HrSupport support;
    private final StaffService staff;
    private final ApprovalEngine engine;
    private final AuditService audit;
    private final Clock clock;

    record Row(UUID id, UUID orgId, UUID staffId, UUID branchId, String type, LocalDate startDate, LocalDate endDate, BigDecimal days, String status,
              UUID approvalRequestId) {
    }

    @Transactional(readOnly = true)
    public PageResponse<HrDtos.LeaveView> search(AccessScope scope, HrDtos.LeaveSearch req) {
        HrDtos.LeaveSearch.LeaveFilters f = req == null || req.filters() == null ? new HrDtos.LeaveSearch.LeaveFilters(null, null, null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(HrSupport.visibleByBranch(scope, ps, "s"));
        w.append(" and l.staff_id = s.id");
        if (f.staffId() != null) {
            w.append(" and l.staff_id = :st");
            ps.addValue("st", f.staffId());
        }
        if (f.branchId() != null) {
            w.append(" and s.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (HrSupport.hasText(f.status())) {
            w.append(" and l.status = :fs");
            ps.addValue("fs", f.status().trim().toUpperCase());
        }
        if (HrSupport.hasText(f.type())) {
            w.append(" and l.type = :ft");
            ps.addValue("ft", f.type().trim().toUpperCase());
        }
        if (f.from() != null) {
            w.append(" and l.end_date >= :from");
            ps.addValue("from", java.sql.Date.valueOf(f.from()));
        }
        if (f.to() != null) {
            w.append(" and l.start_date <= :to");
            ps.addValue("to", java.sql.Date.valueOf(f.to()));
        }
        String from = " from leave_request l join staff_member s on s.id = l.staff_id join person p on p.id = s.person_id"
                + " left join person dp on dp.id = l.decided_by where ";
        Long total = jdbc.queryForObject("select count(*)" + from + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<HrDtos.LeaveView> rows = jdbc.query(SELECT + from + w + " order by l.created_at desc limit :lim offset :off", ps, (rs, i) -> view(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    private static final String SELECT = "select l.*, s.branch_id as staff_branch_id, trim(p.first_name || ' ' || p.last_name) as staff_name,"
            + " trim(dp.first_name || ' ' || dp.last_name) as decided_by_name";

    @Transactional(readOnly = true)
    public HrDtos.LeaveView get(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String vis = HrSupport.visibleByBranch(scope, ps, "s");
        return jdbc.query(SELECT + " from leave_request l join staff_member s on s.id = l.staff_id join person p on p.id = s.person_id"
                        + " left join person dp on dp.id = l.decided_by where l.id = :id and " + vis, ps, (rs, i) -> view(rs)).stream()
                .findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    Row raw(UUID id) {
        List<Row> r = jdbc.query("select l.id, l.organization_id, l.staff_id, s.branch_id, l.type, l.start_date, l.end_date, l.days, l.status, l.approval_request_id"
                        + " from leave_request l join staff_member s on s.id = l.staff_id where l.id = :id", new MapSqlParameterSource("id", id),
                (rs, i) -> new Row((UUID) rs.getObject(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3), (UUID) rs.getObject(4), rs.getString(5),
                        rs.getDate(6).toLocalDate(), rs.getDate(7).toLocalDate(), rs.getBigDecimal(8), rs.getString(9), (UUID) rs.getObject(10)));
        if (r.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return r.get(0);
    }

    @Transactional
    public HrDtos.LeaveView submit(AuthenticatedActor actor, AccessScope scope, HrDtos.LeaveSubmitRequest r) {
        authz.require(actor, HrSupport.MOD_LEAVE, Action.C);
        if (r == null || r.staffId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "personal");
        }
        StaffService.Row staffRow = staff.raw(r.staffId());
        if (!scope.canSeeBranch(staffRow.branchId())) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if ("TERMINATED".equals(staffRow.status())) {
            throw new Exceptions("error.hr.staffTerminated", HttpStatus.CONFLICT);
        }
        String type = r.type() == null ? null : r.type().trim().toUpperCase();
        if (type == null || !TYPES.contains(type)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
        }
        if (r.startDate() == null || r.endDate() == null || r.endDate().isBefore(r.startDate())) {
            throw new Exceptions("error.hr.leaveRange", HttpStatus.BAD_REQUEST);                                            // [V10]
        }
        LocalDate today = support.today();
        if (!"SICK_LEAVE".equals(type) && r.startDate().isBefore(today)) {
            throw new Exceptions("error.hr.leavePast", HttpStatus.BAD_REQUEST);                                             // [V12]
        }
        int days = support.businessDays(scope.organizationId(), r.startDate(), r.endDate());
        if (days <= 0) {
            throw new Exceptions("error.hr.leaveRange", HttpStatus.BAD_REQUEST);
        }
        String attachment = HrSupport.trim(r.attachmentKey(), 300, "adjunto");
        if ("SICK_LEAVE".equals(type) && days > 3 && attachment == null) {
            throw new Exceptions("error.hr.sickAttachment", HttpStatus.UNPROCESSABLE_ENTITY);                               // [V13]
        }
        BigDecimal daysDec = BigDecimal.valueOf(days);
        // bloquea la ficha para serializar solicitudes simultáneas del mismo personal (solape y saldo)
        jdbc.queryForList("select id from staff_member where id = :id for update", new MapSqlParameterSource("id", r.staffId()), UUID.class);
        Integer overlap = jdbc.queryForObject("select count(*) from leave_request where staff_id = :s and status = 'APPROVED' and start_date <= :e and end_date >= :sd",
                new MapSqlParameterSource("s", r.staffId()).addValue("e", java.sql.Date.valueOf(r.endDate())).addValue("sd", java.sql.Date.valueOf(r.startDate())), Integer.class);
        if (overlap != null && overlap > 0) {
            throw new Exceptions("error.hr.leaveOverlap", HttpStatus.CONFLICT);                                             // [V10]
        }
        if ("VACATION".equals(type)) {
            BigDecimal[] bal = lockBalance(r.staffId(), r.startDate().getYear());
            BigDecimal available = bal[0].subtract(bal[1]).subtract(bal[2]);
            if (daysDec.compareTo(available) > 0) {
                throw new Exceptions("error.hr.leaveBalance", HttpStatus.UNPROCESSABLE_ENTITY);                             // [V11]
            }
            jdbc.update("update leave_balance set pending = pending + :d where staff_id = :s and year = :y",
                    new MapSqlParameterSource("d", daysDec).addValue("s", r.staffId()).addValue("y", r.startDate().getYear()));
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into leave_request (id, organization_id, staff_id, type, start_date, end_date, days, reason, attachment_key, status,"
                        + " created_at, created_by) values (:id, :o, :s, :t, :sd, :ed, :d, :r, :att, 'PENDING', :at, :by)",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("s", r.staffId()).addValue("t", type)
                        .addValue("sd", java.sql.Date.valueOf(r.startDate())).addValue("ed", java.sql.Date.valueOf(r.endDate())).addValue("d", daysDec)
                        .addValue("r", HrSupport.trim(r.reason(), 500, "motivo")).addValue("att", attachment).addValue("at", Timestamp.from(clock.instant()))
                        .addValue("by", scope.personId()));
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("staffName", staffNameOf(r.staffId()));
        payload.put("type", type);
        payload.put("days", daysDec.toPlainString());
        UUID reqId = engine.open(new ApprovalEngine.NewRequest(scope.organizationId(), staffRow.branchId(), null, APPROVAL_TYPE, "LEAVE_REQUEST", id,
                scope.personId(), null, payload));
        jdbc.update("update leave_request set approval_request_id = :r where id = :id", new MapSqlParameterSource("r", reqId).addValue("id", id));
        audit.record(new AuditService.Command(HrSupport.MOD_LEAVE, "SUBMIT", ENTITY, id, scope.organizationId(), staffRow.branchId(),
                Map.of("type", type, "days", daysDec.toPlainString())));
        return get(scope, id);
    }

    // ---------------------------------------------------------------- decisión

    @Transactional
    public void approve(AuthenticatedActor actor, AccessScope scope, UUID id, String note) {
        Row r = raw(id);
        if (r.approvalRequestId() == null) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, r.status());
        }
        engine.approve(actor, scope, r.approvalRequestId(), note);
    }

    @Transactional
    public void reject(AuthenticatedActor actor, AccessScope scope, UUID id, String reason) {
        Row r = raw(id);
        if (r.approvalRequestId() == null) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, r.status());
        }
        engine.reject(actor, scope, r.approvalRequestId(), reason);
    }

    void onApproved(ApprovalHandler.Decision d) {
        Row r = raw(d.request().subjectId());
        jdbc.update("update leave_request set status = 'APPROVED', decided_by = :by, updated_at = :at, version = version + 1 where id = :id and status = 'PENDING'",
                new MapSqlParameterSource("by", d.actorPersonId()).addValue("at", Timestamp.from(clock.instant())).addValue("id", r.id()));
        if ("VACATION".equals(r.type())) {
            jdbc.update("update leave_balance set pending = pending - :d, taken = taken + :d where staff_id = :s and year = :y",
                    new MapSqlParameterSource("d", r.days()).addValue("s", r.staffId()).addValue("y", r.startDate().getYear()));
        }
    }

    void onRejected(ApprovalHandler.Decision d) {
        Row r = raw(d.request().subjectId());
        jdbc.update("update leave_request set status = 'REJECTED', decided_by = :by, decision_reason = :r, updated_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("by", d.actorPersonId()).addValue("r", d.note()).addValue("at", Timestamp.from(clock.instant())).addValue("id", r.id()));
        releasePending(r);
    }

    void onCancelled(ApprovalHandler.Decision d) {
        Row r = raw(d.request().subjectId());
        jdbc.update("update leave_request set status = 'CANCELLED', updated_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("id", r.id()));
        releasePending(r);
    }

    private void releasePending(Row r) {
        if ("VACATION".equals(r.type())) {
            jdbc.update("update leave_balance set pending = pending - :d where staff_id = :s and year = :y",
                    new MapSqlParameterSource("d", r.days()).addValue("s", r.staffId()).addValue("y", r.startDate().getYear()));
        }
    }

    Map<String, String> notifyParams(ApprovalHandler.ApprovalRow r) {
        Map<String, String> m = new java.util.HashMap<>();
        m.put("staff", String.valueOf(r.payload().get("staffName")));
        m.put("type", String.valueOf(r.payload().get("type")));
        m.put("days", String.valueOf(r.payload().get("days")));
        return m;
    }

    /** Cancela una solicitud: PENDING vía el motor (libera el saldo reservado); APPROVED solo antes de iniciar (restaura lo tomado). */
    @Transactional
    public void cancel(AuthenticatedActor actor, AccessScope scope, UUID id, String reason) {
        authz.require(actor, HrSupport.MOD_LEAVE, Action.S);
        Row r = raw(id);
        if ("PENDING".equals(r.status())) {
            if (r.approvalRequestId() != null) {
                engine.decideInternal(r.approvalRequestId(), false, scope.personId(), reason == null ? "Cancelada" : reason);
            }
            jdbc.update("update leave_request set status = 'CANCELLED', decision_reason = :r, updated_at = :at, version = version + 1 where id = :id",
                    new MapSqlParameterSource("r", reason).addValue("at", Timestamp.from(clock.instant())).addValue("id", id));
        } else if ("APPROVED".equals(r.status())) {
            if (!r.startDate().isAfter(support.today())) {
                throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, r.status());
            }
            jdbc.update("update leave_request set status = 'CANCELLED', decision_reason = :r, updated_at = :at, version = version + 1 where id = :id",
                    new MapSqlParameterSource("r", reason).addValue("at", Timestamp.from(clock.instant())).addValue("id", id));
            if ("VACATION".equals(r.type())) {
                jdbc.update("update leave_balance set taken = taken - :d where staff_id = :s and year = :y",
                        new MapSqlParameterSource("d", r.days()).addValue("s", r.staffId()).addValue("y", r.startDate().getYear()));
            }
        } else {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, r.status());
        }
        audit.record(new AuditService.Command(HrSupport.MOD_LEAVE, "CANCEL", ENTITY, id, scope.organizationId(), r.branchId(), Map.of()));
    }

    // ---------------------------------------------------------------- saldo

    private BigDecimal[] lockBalance(UUID staffId, int year) {
        jdbc.update("insert into leave_balance (id, staff_id, year, entitled, taken, pending) values (:id, :s, :y, :e, 0, 0)"
                        + " on conflict (staff_id, year) do nothing",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("s", staffId).addValue("y", year).addValue("e", HrSupport.DEFAULT_VACATION_DAYS));
        return jdbc.query("select entitled, taken, pending from leave_balance where staff_id = :s and year = :y for update",
                new MapSqlParameterSource("s", staffId).addValue("y", year), (rs, i) -> new BigDecimal[]{rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3)}).get(0);
    }

    @Transactional(readOnly = true)
    public List<HrDtos.LeaveBalanceView> balances(AccessScope scope, UUID staffId, Integer year) {
        int y = year == null ? support.today().getYear() : year;
        StaffService.Row s = staff.raw(staffId);
        if (!scope.canSeeBranch(s.branchId())) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        jdbc.update("insert into leave_balance (id, staff_id, year, entitled, taken, pending) values (:id, :s, :y, :e, 0, 0) on conflict (staff_id, year) do nothing",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("s", staffId).addValue("y", y).addValue("e", HrSupport.DEFAULT_VACATION_DAYS));
        return jdbc.query("select l.staff_id, l.entitled, l.taken, l.pending, trim(p.first_name || ' ' || p.last_name) from leave_balance l"
                        + " join staff_member s on s.id = l.staff_id join person p on p.id = s.person_id where l.staff_id = :s and l.year = :y",
                new MapSqlParameterSource("s", staffId).addValue("y", y),
                (rs, i) -> new HrDtos.LeaveBalanceView((UUID) rs.getObject(1), rs.getString(5), y, rs.getBigDecimal(2).toPlainString(), rs.getBigDecimal(3).toPlainString(),
                        rs.getBigDecimal(4).toPlainString(), rs.getBigDecimal(2).subtract(rs.getBigDecimal(3)).subtract(rs.getBigDecimal(4)).toPlainString()));
    }

    /** Política de vacaciones por personal y año (ORG_ADMIN, `E` de HR_LEAVE): ajusta `entitled`. */
    @Transactional
    public HrDtos.LeaveBalanceView adjustBalance(AuthenticatedActor actor, AccessScope scope, UUID staffId, int year, HrDtos.LeaveBalanceAdjustRequest r) {
        authz.require(actor, HrSupport.MOD_LEAVE, Action.E);
        BigDecimal entitled = HrSupport.requiredAmount(r == null ? null : r.entitled(), "días");
        if (entitled.compareTo(BigDecimal.ZERO) < 0) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "días");
        }
        jdbc.update("insert into leave_balance (id, staff_id, year, entitled, taken, pending) values (:id, :s, :y, :e, 0, 0)"
                        + " on conflict (staff_id, year) do update set entitled = :e",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("s", staffId).addValue("y", year).addValue("e", entitled));
        return balances(scope, staffId, year).get(0);
    }

    private String staffNameOf(UUID staffId) {
        List<String> n = jdbc.queryForList("select trim(p.first_name || ' ' || p.last_name) from staff_member s join person p on p.id = s.person_id where s.id = :id",
                new MapSqlParameterSource("id", staffId), String.class);
        return n.isEmpty() ? "" : n.get(0);
    }

    private static HrDtos.LeaveView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new HrDtos.LeaveView((UUID) rs.getObject("id"), (UUID) rs.getObject("staff_id"), rs.getString("staff_name"), (UUID) rs.getObject("staff_branch_id"),
                rs.getString("type"), rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate(), rs.getBigDecimal("days").toPlainString(),
                rs.getString("reason"), rs.getString("attachment_key"), rs.getString("status"), (UUID) rs.getObject("decided_by"), rs.getString("decided_by_name"),
                rs.getString("decision_reason"), (UUID) rs.getObject("approval_request_id"), rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }
}
