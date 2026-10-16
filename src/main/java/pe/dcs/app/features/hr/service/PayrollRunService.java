package pe.dcs.app.features.hr.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.finance.service.FinanceSupport;
import pe.dcs.app.features.finance.service.FinancialMovementService;
import pe.dcs.app.features.hr.dto.HrDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.security.authz.ContractGate;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * M17 · Corridas de planilla (HR_PAYROLL). Flujo: crear (DRAFT) → calcular (CALCULATED, reejecutable [V6]) → aprobar (APPROVED,
 * inmutable, asigna boletas [V8]) → pagar (PAID, genera egreso PENDING en M15 [V7], idempotente por sourceRef/sourceId) →
 * cerrar (CLOSED, terminal). [D2, ver cabecera de la migración] la boleta no usa CertificateService (exige rite_id): contador
 * propio, mismo motivo que M13.
 */
@Service
@RequiredArgsConstructor
public class PayrollRunService {

    private static final Pattern PERIOD = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");
    private static final String ENTITY = "PayrollRun";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final HrSupport support;
    private final PayrollConceptService concepts;
    private final ContractGate contractGate;
    private final ObjectProvider<FinancialMovementService> financeProvider;
    private final AuditService audit;
    private final Clock clock;

    record Row(UUID id, UUID orgId, UUID branchId, String period, String status) {
    }

    @Transactional(readOnly = true)
    public PageResponse<HrDtos.RunView> search(AccessScope scope, HrDtos.RunSearch req) {
        HrDtos.RunSearch.RunFilters f = req == null || req.filters() == null ? new HrDtos.RunSearch.RunFilters(null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId());
        StringBuilder w = new StringBuilder("r.organization_id = :org");
        if (!scope.allBranches()) {
            ps.addValue("scopeBranches", HrSupport.branchIdsOrNone(scope));
            w.append(" and (r.branch_id is null or r.branch_id in (:scopeBranches))");
        }
        if (f.branchId() != null) {
            w.append(" and r.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (HrSupport.hasText(f.status())) {
            w.append(" and r.status = :fs");
            ps.addValue("fs", f.status().trim().toUpperCase());
        }
        if (HrSupport.hasText(f.period())) {
            w.append(" and r.period = :fp");
            ps.addValue("fp", f.period().trim());
        }
        String from = " from payroll_run r left join branch b on b.id = r.branch_id where ";
        Long total = jdbc.queryForObject("select count(*)" + from + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<UUID> ids = jdbc.query("select r.id" + from + w + " order by r.period desc, b.name limit :lim offset :off", ps, (rs, i) -> (UUID) rs.getObject(1));
        long t = total == null ? 0 : total;
        List<HrDtos.RunView> rows = ids.stream().map(id -> get(scope, id)).toList();
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public HrDtos.RunView get(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id).addValue("org", scope.organizationId());
        List<Object[]> r = jdbc.query("select r.id, r.branch_id, b.name, r.period, r.status, r.calculated_at, r.approved_at, r.paid_at, r.closed_at,"
                        + " r.financial_movement_id, r.created_at, r.version from payroll_run r left join branch b on b.id = r.branch_id"
                        + " where r.id = :id and r.organization_id = :org",
                ps, (rs, i) -> new Object[]{rs.getObject(1), rs.getObject(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getTimestamp(6),
                        rs.getTimestamp(7), rs.getTimestamp(8), rs.getTimestamp(9), rs.getObject(10), rs.getTimestamp(11), rs.getLong(12)});
        if (r.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Object[] o = r.get(0);
        UUID branchId = (UUID) o[1];
        if (branchId != null && !scope.canSeeBranch(branchId)) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> totals = jdbc.queryForMap("select coalesce(sum(gross),0) g, coalesce(sum(deductions),0) d, coalesce(sum(net),0) n,"
                + " coalesce(sum(employer_cost),0) ec, count(*) c from payroll_record where run_id = :id", new MapSqlParameterSource("id", id));
        return new HrDtos.RunView((UUID) o[0], branchId, (String) o[2], (String) o[3], (String) o[4],
                o[5] == null ? null : ((Timestamp) o[5]).toInstant(), o[6] == null ? null : ((Timestamp) o[6]).toInstant(),
                o[7] == null ? null : ((Timestamp) o[7]).toInstant(), o[8] == null ? null : ((Timestamp) o[8]).toInstant(), (UUID) o[9],
                ((BigDecimal) totals.get("g")).toPlainString(), ((BigDecimal) totals.get("d")).toPlainString(), ((BigDecimal) totals.get("n")).toPlainString(),
                ((BigDecimal) totals.get("ec")).toPlainString(), ((Number) totals.get("c")).intValue(), ((Timestamp) o[10]).toInstant(), (Long) o[11]);
    }

    private Row lock(AccessScope scope, UUID id) {
        HrDtos.RunView v = get(scope, id);
        jdbc.queryForList("select id from payroll_run where id = :id for update", new MapSqlParameterSource("id", id), UUID.class);
        return new Row(v.id(), scope.organizationId(), v.branchId(), v.period(), v.status());
    }

    @Transactional
    public HrDtos.RunView create(AuthenticatedActor actor, AccessScope scope, HrDtos.RunCreateRequest r) {
        authz.require(actor, HrSupport.MOD_PAYROLL, Action.C);
        if (r == null || r.period() == null || !PERIOD.matcher(r.period().trim()).matches()) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "periodo");
        }
        UUID branchId = r.branchId();
        if (branchId != null && !scope.canSeeBranch(branchId)) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into payroll_run (id, organization_id, branch_id, period, status, created_at, created_by) values (:id, :o, :b, :p, 'DRAFT', :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", branchId).addValue("p", r.period().trim())
                            .addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()));
        } catch (DuplicateKeyException e) {
            throw new Exceptions("error.hr.runExists", HttpStatus.CONFLICT);                                                // [V5]
        }
        audit.record(new AuditService.Command(HrSupport.MOD_PAYROLL, "CREATE", ENTITY, id, scope.organizationId(), branchId, Map.of("period", r.period())));
        return get(scope, id);
    }

    /** [V6] reejecutable hasta APPROVED, mismo resultado con el mismo fixture; después de APPROVED → 409 runLocked. */
    @Transactional
    public HrDtos.RunView calculate(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, HrSupport.MOD_PAYROLL, Action.E);
        Row run = lock(scope, id);
        if (!("DRAFT".equals(run.status()) || "CALCULATED".equals(run.status()))) {
            throw new Exceptions("error.hr.runLocked", HttpStatus.CONFLICT);
        }
        YearMonth ym = YearMonth.parse(run.period());
        LocalDate periodStart = ym.atDay(1);
        LocalDate periodEnd = ym.atEndOfMonth();
        MapSqlParameterSource sps = new MapSqlParameterSource("o", run.orgId()).addValue("he", java.sql.Date.valueOf(periodEnd))
                .addValue("ps", java.sql.Date.valueOf(periodStart));
        StringBuilder w = new StringBuilder("s.organization_id = :o and s.hire_date <= :he and (s.termination_date is null or s.termination_date >= :ps)");
        if (run.branchId() != null) {
            w.append(" and s.branch_id = :b");
            sps.addValue("b", run.branchId());
        }
        List<StaffRow> staffList = jdbc.query("select s.id, s.base_salary, trim(p.first_name||' '||p.last_name) from staff_member s join person p on p.id = s.person_id"
                        + " where " + w, sps, (rs, i) -> new StaffRow((UUID) rs.getObject(1), rs.getBigDecimal(2), rs.getString(3)));
        jdbc.update("delete from payroll_record_line where record_id in (select id from payroll_record where run_id = :id)", new MapSqlParameterSource("id", id));
        jdbc.update("delete from payroll_record where run_id = :id", new MapSqlParameterSource("id", id));
        List<PayrollConceptService.Row> activeConcepts = concepts.activeForCalc(run.orgId());
        int totalWorked = support.businessDays(run.orgId(), periodStart, periodEnd);
        for (StaffRow s : staffList) {
            BigDecimal unpaidDays = unpaidLeaveDays(s.id(), periodStart, periodEnd);
            BigDecimal workedDays = BigDecimal.valueOf(totalWorked).subtract(unpaidDays).max(BigDecimal.ZERO);
            BigDecimal proration = totalWorked == 0 ? BigDecimal.ONE : workedDays.divide(BigDecimal.valueOf(totalWorked), 6, java.math.RoundingMode.HALF_UP);
            BigDecimal gross = BigDecimal.ZERO, deductions = BigDecimal.ZERO, employerCost = BigDecimal.ZERO;
            UUID recordId = UUID.randomUUID();
            List<Object[]> lines = new ArrayList<>();
            for (PayrollConceptService.Row c : activeConcepts) {
                if ("MANUAL".equals(c.calc())) {
                    continue;                                                                                             // [D3, ver cabecera] MANUAL se ajusta aparte; el cálculo automático solo cubre FIXED/PERCENT_OF_BASE
                }
                BigDecimal amount = "PERCENT_OF_BASE".equals(c.calc()) ? s.baseSalary().multiply(c.value()).divide(new BigDecimal("100"), 10, java.math.RoundingMode.HALF_UP)
                        : c.value();
                amount = HrSupport.scale2(amount.multiply(proration));
                if (amount.signum() == 0) {
                    continue;
                }
                lines.add(new Object[]{c.id(), c.code(), c.nameEs(), c.kind(), amount});
                switch (c.kind()) {
                    case "EARNING" -> gross = gross.add(amount);
                    case "DEDUCTION" -> deductions = deductions.add(amount);
                    case "EMPLOYER_CONTRIBUTION" -> employerCost = employerCost.add(amount);
                    default -> { }
                }
            }
            BigDecimal net = HrSupport.scale2(gross.subtract(deductions));
            jdbc.update("insert into payroll_record (id, run_id, staff_id, gross, deductions, net, employer_cost, worked_days, unpaid_days, created_at)"
                            + " values (:id, :r, :s, :g, :d, :n, :ec, :wd, :ud, :at)",
                    new MapSqlParameterSource("id", recordId).addValue("r", id).addValue("s", s.id()).addValue("g", gross).addValue("d", deductions)
                            .addValue("n", net).addValue("ec", employerCost).addValue("wd", workedDays).addValue("ud", unpaidDays).addValue("at", Timestamp.from(clock.instant())));
            for (Object[] l : lines) {
                jdbc.update("insert into payroll_record_line (id, record_id, concept_id, concept_code, concept_name, kind, amount) values (:id, :r, :c, :cc, :cn, :k, :a)",
                        new MapSqlParameterSource("id", UUID.randomUUID()).addValue("r", recordId).addValue("c", l[0]).addValue("cc", l[1]).addValue("cn", l[2])
                                .addValue("k", l[3]).addValue("a", l[4]));
            }
        }
        jdbc.update("update payroll_run set status = 'CALCULATED', calculated_at = :at, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
        audit.record(new AuditService.Command(HrSupport.MOD_PAYROLL, "CALCULATE", ENTITY, id, run.orgId(), run.branchId(), Map.of("staff", staffList.size())));
        return get(scope, id);
    }

    private record StaffRow(UUID id, BigDecimal baseSalary, String name) {
    }

    /** Días de UNPAID_LEAVE (APPROVED) dentro del periodo, para prorratear el sueldo del mes. */
    private BigDecimal unpaidLeaveDays(UUID staffId, LocalDate periodStart, LocalDate periodEnd) {
        List<BigDecimal> d = jdbc.query("select days from leave_request where staff_id = :s and status = 'APPROVED' and type = 'UNPAID_LEAVE'"
                        + " and start_date <= :e and end_date >= :s2",
                new MapSqlParameterSource("s", staffId).addValue("e", java.sql.Date.valueOf(periodEnd)).addValue("s2", java.sql.Date.valueOf(periodStart)),
                (rs, i) -> rs.getBigDecimal(1));
        return d.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public List<HrDtos.RecordView> records(AuthenticatedActor actor, AccessScope scope, UUID runId) {
        get(scope, runId);
        boolean h = authz.effectiveActions(actor, HrSupport.MOD_PAYROLL).contains(Action.H.name());
        List<HrDtos.RecordView> out = new ArrayList<>();
        for (var row : jdbc.query("select pr.id, pr.staff_id, trim(p.first_name||' '||p.last_name), s.position, pr.gross, pr.deductions, pr.net,"
                        + " pr.employer_cost, pr.worked_days, pr.unpaid_days, pr.payslip_no from payroll_record pr join staff_member s on s.id = pr.staff_id"
                        + " join person p on p.id = s.person_id where pr.run_id = :r order by p.first_name, p.last_name",
                new MapSqlParameterSource("r", runId), (rs, i) -> new Object[]{rs.getObject(1), rs.getObject(2), rs.getString(3), rs.getString(4),
                        rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getBigDecimal(9), rs.getBigDecimal(10), rs.getString(11)})) {
            UUID recordId = (UUID) row[0];
            List<HrDtos.RecordLineView> lines = h ? jdbc.query("select concept_code, concept_name, kind, amount from payroll_record_line where record_id = :id order by kind",
                    new MapSqlParameterSource("id", recordId), (rs, i) -> new HrDtos.RecordLineView(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4).toPlainString()))
                    : List.of();
            out.add(new HrDtos.RecordView(recordId, (UUID) row[1], (String) row[2], (String) row[3], h ? ((BigDecimal) row[4]).toPlainString() : null,
                    h ? ((BigDecimal) row[5]).toPlainString() : null, h ? ((BigDecimal) row[6]).toPlainString() : null, h ? ((BigDecimal) row[7]).toPlainString() : null,
                    ((BigDecimal) row[8]).toPlainString(), ((BigDecimal) row[9]).toPlainString(), (String) row[10], lines, h));
        }
        return out;
    }

    /** [V6] APPROVED es inmutable; asigna aquí el correlativo de boleta [V8] sin huecos. */
    @Transactional
    public HrDtos.RunView approve(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, HrSupport.MOD_PAYROLL, Action.A);
        Row run = lock(scope, id);
        if (!"CALCULATED".equals(run.status())) {
            throw new Exceptions("error.hr.runLocked", HttpStatus.CONFLICT);
        }
        int year = YearMonth.parse(run.period()).getYear();
        List<UUID> recordIds = jdbc.queryForList("select id from payroll_record where run_id = :id order by created_at", new MapSqlParameterSource("id", id), UUID.class);
        for (UUID recordId : recordIds) {
            String no = nextPayslipNo(run.orgId(), year);
            jdbc.update("update payroll_record set payslip_no = :n, updated_at = :at where id = :id",
                    new MapSqlParameterSource("n", no).addValue("at", Timestamp.from(clock.instant())).addValue("id", recordId));
        }
        jdbc.update("update payroll_run set status = 'APPROVED', approved_at = :at, approved_by = :by, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
        audit.record(new AuditService.Command(HrSupport.MOD_PAYROLL, "APPROVE", ENTITY, id, run.orgId(), run.branchId(), Map.of()));
        return get(scope, id);
    }

    private String nextPayslipNo(UUID orgId, int year) {
        Integer n = jdbc.queryForObject("insert into payroll_record_counter (organization_id, year, last_number) values (:o, :y, 1)"
                        + " on conflict (organization_id, year) do update set last_number = payroll_record_counter.last_number + 1 returning last_number",
                new MapSqlParameterSource("o", orgId).addValue("y", year), Integer.class);
        return "BOL-" + year + "-" + String.format("%06d", n);
    }

    /** [V7] PAID exige el egreso PENDING en M15 (idempotente por source_ref/source_id): sin FIN_MOVEMENTS contratado, o sin fondo, no se puede pagar. */
    @Transactional
    public HrDtos.RunView pay(AuthenticatedActor actor, AccessScope scope, UUID id, HrDtos.RunPayRequest r) {
        authz.require(actor, HrSupport.MOD_PAYROLL, Action.P);
        Row run = lock(scope, id);
        if (!"APPROVED".equals(run.status())) {
            throw new Exceptions("error.hr.runLocked", HttpStatus.CONFLICT);
        }
        UUID branchId = run.branchId() != null ? run.branchId() : (r == null ? null : r.branchId());
        UUID fundId = r == null ? null : r.fundId();
        if (branchId == null || fundId == null || !contractGate.enabled(run.orgId(), FinanceSupport.MOD_MOVEMENTS)) {
            throw new Exceptions("error.hr.payrollNoMovement", HttpStatus.UNPROCESSABLE_ENTITY);                            // [V7]
        }
        BigDecimal net = (BigDecimal) jdbc.queryForMap("select coalesce(sum(net),0) n from payroll_record where run_id = :id", new MapSqlParameterSource("id", id)).get("n");
        if (net.signum() <= 0) {
            throw new Exceptions("error.hr.payrollNoMovement", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        FinancialMovementService finance = financeProvider.getObject();
        LocalDate payDate = support.today();
        UUID finMovementId = finance.createFromSource(run.orgId(), branchId, payDate, "PAYROLL", fundId, net, "Planilla " + run.period(), "PAYROLL_RUN", id, scope.personId());
        jdbc.update("update payroll_run set status = 'PAID', paid_at = :at, paid_by = :by, financial_movement_id = :fm, updated_at = :at, updated_by = :by,"
                        + " version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("fm", finMovementId).addValue("id", id));
        audit.record(new AuditService.Command(HrSupport.MOD_PAYROLL, "PAY", ENTITY, id, run.orgId(), run.branchId(), Map.of("net", net.toPlainString())));
        return get(scope, id);
    }

    @Transactional
    public HrDtos.RunView close(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, HrSupport.MOD_PAYROLL, Action.K);
        Row run = lock(scope, id);
        if (!"PAID".equals(run.status())) {
            throw new Exceptions("error.hr.runLocked", HttpStatus.CONFLICT);
        }
        jdbc.update("update payroll_run set status = 'CLOSED', closed_at = :at, closed_by = :by, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
        audit.record(new AuditService.Command(HrSupport.MOD_PAYROLL, "CLOSE", ENTITY, id, run.orgId(), run.branchId(), Map.of()));
        return get(scope, id);
    }

    /** Anula una corrida antes de aprobarla (DRAFT/CALCULATED); después de APPROVED la corrección va en el siguiente periodo [V6]. */
    @Transactional
    public void voidRun(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, HrSupport.MOD_PAYROLL, Action.Y);
        Row run = lock(scope, id);
        if (!("DRAFT".equals(run.status()) || "CALCULATED".equals(run.status()))) {
            throw new Exceptions("error.hr.runLocked", HttpStatus.CONFLICT);
        }
        jdbc.update("delete from payroll_record_line where record_id in (select id from payroll_record where run_id = :id)", new MapSqlParameterSource("id", id));
        jdbc.update("delete from payroll_record where run_id = :id", new MapSqlParameterSource("id", id));
        jdbc.update("delete from payroll_run where id = :id", new MapSqlParameterSource("id", id));
        audit.record(new AuditService.Command(HrSupport.MOD_PAYROLL, "VOID", ENTITY, id, run.orgId(), run.branchId(), Map.of()));
    }
}
