package pe.dcs.app.features.hr.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.hr.dto.HrDtos;
import pe.dcs.app.features.person.service.PersonLookupService;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M17 · Personal (HR_STAFF): ficha del personal (pastoral y administrativo), historial de sueldo y cese. [V1] una ficha
 * no-TERMINATED por persona (índice único parcial); recontratar tras un cese abre una ficha nueva. [V9] sueldo/dato bancario
 * enmascarados sin la acción H.
 */
@Service
@RequiredArgsConstructor
public class StaffService {

    private static final Set<String> CONTRACT_TYPES = Set.of("PLANILLA_INDEFINIDO", "PLANILLA_PLAZO_FIJO", "RECIBO_HONORARIOS", "PRACTICAS", "OTRO");
    private static final Set<String> CONTRACT_END_REQUIRED = Set.of("PLANILLA_PLAZO_FIJO", "PRACTICAS");
    private static final Set<String> PAY_FREQUENCIES = Set.of("MONTHLY", "BIWEEKLY", "WEEKLY");
    private static final String ENTITY = "StaffMember";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final HrSupport support;
    private final PersonLookupService persons;
    private final AuditService audit;
    private final Clock clock;

    record Row(UUID id, UUID orgId, UUID branchId, UUID personId, String status, LocalDate hireDate, LocalDate terminationDate, BigDecimal baseSalary) {
    }

    @Transactional(readOnly = true)
    public PageResponse<HrDtos.StaffView> search(AuthenticatedActor actor, AccessScope scope, HrDtos.StaffSearch req) {
        HrDtos.StaffSearch.StaffFilters f = req == null || req.filters() == null ? new HrDtos.StaffSearch.StaffFilters(null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(HrSupport.visibleByBranch(scope, ps, "s"));
        if (f.branchId() != null) {
            w.append(" and s.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (HrSupport.hasText(f.status())) {
            w.append(" and s.status = :fs");
            ps.addValue("fs", f.status().trim().toUpperCase());
        }
        if (HrSupport.hasText(f.contractType())) {
            w.append(" and s.contract_type = :fc");
            ps.addValue("fc", f.contractType().trim().toUpperCase());
        }
        if (HrSupport.hasText(f.q())) {
            w.append(" and lower(p.first_name || ' ' || p.last_name || ' ' || s.position) like :q");
            ps.addValue("q", "%" + f.q().trim().toLowerCase() + "%");
        }
        String from = " from staff_member s left join branch b on b.id = s.branch_id join person p on p.id = s.person_id where ";
        Long total = jdbc.queryForObject("select count(*)" + from + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        boolean h = hasSensitive(actor);
        List<HrDtos.StaffView> rows = jdbc.query(SELECT + from + w + " order by p.first_name, p.last_name limit :lim offset :off", ps, (rs, i) -> view(rs, h));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    private static final String SELECT = "select s.*, b.name as branch_name, trim(p.first_name || ' ' || p.last_name) as person_name, m.name as ministry_name";
    private static final String JOINS = " left join ministry m on m.id = s.ministry_id";

    @Transactional(readOnly = true)
    public HrDtos.StaffView get(AuthenticatedActor actor, AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String vis = HrSupport.visibleByBranch(scope, ps, "s");
        boolean h = hasSensitive(actor);
        return jdbc.query(SELECT + " from staff_member s left join branch b on b.id = s.branch_id join person p on p.id = s.person_id" + JOINS
                        + " where s.id = :id and " + vis, ps, (rs, i) -> view(rs, h)).stream()
                .findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    Row raw(UUID id) {
        List<Row> r = jdbc.query("select id, organization_id, branch_id, person_id, status, hire_date, termination_date, base_salary from staff_member where id = :id",
                new MapSqlParameterSource("id", id), (rs, i) -> new Row((UUID) rs.getObject(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3),
                        (UUID) rs.getObject(4), rs.getString(5), rs.getDate(6).toLocalDate(), rs.getDate(7) == null ? null : rs.getDate(7).toLocalDate(),
                        rs.getBigDecimal(8)));
        if (r.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return r.get(0);
    }

    private Row lock(UUID id) {
        Row r = raw(id);
        jdbc.queryForList("select id from staff_member where id = :id for update", new MapSqlParameterSource("id", id), UUID.class);
        return r;
    }

    private boolean hasSensitive(AuthenticatedActor actor) {
        return authz.effectiveActions(actor, HrSupport.MOD_STAFF).contains(Action.H.name());
    }

    @Transactional
    public HrDtos.StaffView create(AuthenticatedActor actor, AccessScope scope, HrDtos.StaffRequest r) {
        authz.require(actor, HrSupport.MOD_STAFF, Action.C);
        UUID branchId = validateBranch(scope, r == null ? null : r.branchId());
        if (r.personId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona");
        }
        var person = persons.getVisible(scope, r.personId());
        LocalDate today = support.today();
        if (person.birthDate() == null || person.birthDate().isAfter(today.minusYears(18))) {
            throw new Exceptions("error.hr.notAdult", HttpStatus.UNPROCESSABLE_ENTITY);                                     // [V1]
        }
        String position = HrSupport.trim(r.position(), 120, "cargo");
        if (position == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "cargo");
        }
        String contractType = validContractType(r.contractType());
        if (r.hireDate() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "fecha de ingreso");
        }
        if (CONTRACT_END_REQUIRED.contains(contractType) && r.contractEnd() == null) {
            throw new Exceptions("error.hr.contractEndRequired", HttpStatus.UNPROCESSABLE_ENTITY);                          // [V2]
        }
        BigDecimal salary = validSalary(r.baseSalary());
        String payFrequency = r.payFrequency() == null || !PAY_FREQUENCIES.contains(r.payFrequency().trim().toUpperCase()) ? "MONTHLY" : r.payFrequency().trim().toUpperCase();
        String currency = HrSupport.hasText(r.currency()) ? r.currency().trim().toUpperCase() : "PEN";
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into staff_member (id, organization_id, branch_id, person_id, position, ministry_id, contract_type, hire_date, contract_end,"
                            + " base_salary, currency, pay_frequency, bank_encrypted, status, created_at, created_by) values (:id, :o, :b, :p, :pos, :min,"
                            + " :ct, :hd, :ce, :sal, :cur, :pf, :bank, 'ACTIVE', :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", branchId).addValue("p", r.personId())
                            .addValue("pos", position).addValue("min", r.ministryId()).addValue("ct", contractType).addValue("hd", java.sql.Date.valueOf(r.hireDate()))
                            .addValue("ce", r.contractEnd() == null ? null : java.sql.Date.valueOf(r.contractEnd())).addValue("sal", salary).addValue("cur", currency)
                            .addValue("pf", payFrequency).addValue("bank", support.encryptBank(r.bank())).addValue("at", Timestamp.from(clock.instant()))
                            .addValue("by", scope.personId()));
        } catch (DuplicateKeyException e) {
            throw new Exceptions("error.hr.alreadyStaff", HttpStatus.CONFLICT);                                              // [V1]
        }
        jdbc.update("insert into salary_history (id, staff_id, amount, from_date, reason, created_at, created_by) values (:id, :s, :a, :f, :r, :at, :by)",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("s", id).addValue("a", salary).addValue("f", java.sql.Date.valueOf(r.hireDate()))
                        .addValue("r", "Alta").addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()));
        audit.record(new AuditService.Command(HrSupport.MOD_STAFF, "CREATE", ENTITY, id, scope.organizationId(), branchId, Map.of("position", position)));
        return get(actor, scope, id);
    }

    @Transactional
    public HrDtos.StaffView update(AuthenticatedActor actor, AccessScope scope, UUID id, HrDtos.StaffRequest r) {
        authz.require(actor, HrSupport.MOD_STAFF, Action.E);
        Row cur = lock(id);
        String position = HrSupport.trim(r.position(), 120, "cargo");
        if (position == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "cargo");
        }
        String contractType = validContractType(r.contractType());
        if (CONTRACT_END_REQUIRED.contains(contractType) && r.contractEnd() == null) {
            throw new Exceptions("error.hr.contractEndRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String payFrequency = r.payFrequency() == null || !PAY_FREQUENCIES.contains(r.payFrequency().trim().toUpperCase()) ? "MONTHLY" : r.payFrequency().trim().toUpperCase();
        int n = jdbc.update("update staff_member set position = :pos, ministry_id = :min, contract_type = :ct, contract_end = :ce, pay_frequency = :pf,"
                        + " bank_encrypted = :bank, updated_at = :at, updated_by = :by, version = version + 1"
                        + " where id = :id and (cast(:v as bigint) is null or version = :v)",
                new MapSqlParameterSource("pos", position).addValue("min", r.ministryId()).addValue("ct", contractType)
                        .addValue("ce", r.contractEnd() == null ? null : java.sql.Date.valueOf(r.contractEnd())).addValue("pf", payFrequency)
                        .addValue("bank", support.encryptBank(r.bank())).addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId())
                        .addValue("id", id).addValue("v", r.version()));
        if (n == 0) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(HrSupport.MOD_STAFF, "UPDATE", ENTITY, id, scope.organizationId(), cur.branchId(), Map.of()));
        return get(actor, scope, id);
    }

    /** Cambio de sueldo: guarda historial y actualiza el sueldo vigente; los periodos ya calculados conservan su propio importe [T12]. */
    @Transactional
    public HrDtos.StaffView changeSalary(AuthenticatedActor actor, AccessScope scope, UUID id, HrDtos.SalaryChangeRequest r) {
        authz.require(actor, HrSupport.MOD_STAFF, Action.E);
        Row cur = lock(id);
        if ("TERMINATED".equals(cur.status())) {
            throw new Exceptions("error.hr.staffTerminated", HttpStatus.CONFLICT);
        }
        BigDecimal amount = validSalary(r == null ? null : r.amount());
        LocalDate from = r != null && r.fromDate() != null ? r.fromDate() : support.today();
        jdbc.update("update staff_member set base_salary = :a, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("a", amount).addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
        jdbc.update("insert into salary_history (id, staff_id, amount, from_date, reason, created_at, created_by) values (:id, :s, :a, :f, :r, :at, :by)",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("s", id).addValue("a", amount).addValue("f", java.sql.Date.valueOf(from))
                        .addValue("r", HrSupport.trim(r == null ? null : r.reason(), 300, "motivo")).addValue("at", Timestamp.from(clock.instant()))
                        .addValue("by", scope.personId()));
        audit.record(new AuditService.Command(HrSupport.MOD_STAFF, "SALARY_CHANGE", ENTITY, id, scope.organizationId(), cur.branchId(), Map.of("amount", amount.toPlainString())));
        return get(actor, scope, id);
    }

    @Transactional
    public HrDtos.StaffView terminate(AuthenticatedActor actor, AccessScope scope, UUID id, HrDtos.TerminateRequest r) {
        authz.require(actor, HrSupport.MOD_STAFF, Action.S);
        Row cur = lock(id);
        if ("TERMINATED".equals(cur.status())) {
            throw new Exceptions("error.hr.staffTerminated", HttpStatus.CONFLICT);
        }
        LocalDate termination = r == null || r.terminationDate() == null ? support.today() : r.terminationDate();
        if (termination.isBefore(cur.hireDate())) {
            throw new Exceptions("error.hr.terminationBeforeHire", HttpStatus.UNPROCESSABLE_ENTITY);                        // [V2]
        }
        String reason = HrSupport.trim(r == null ? null : r.reason(), 300, "motivo");
        jdbc.update("update staff_member set status = 'TERMINATED', termination_date = :td, termination_reason = :tr, updated_at = :at, updated_by = :by,"
                        + " version = version + 1 where id = :id",
                new MapSqlParameterSource("td", java.sql.Date.valueOf(termination)).addValue("tr", reason).addValue("at", Timestamp.from(clock.instant()))
                        .addValue("by", scope.personId()).addValue("id", id));
        audit.record(new AuditService.Command(HrSupport.MOD_STAFF, "TERMINATE", ENTITY, id, scope.organizationId(), cur.branchId(), Map.of("terminationDate", termination.toString())));
        return get(actor, scope, id);
    }

    @Transactional(readOnly = true)
    public List<HrDtos.SalaryHistoryView> salaryHistory(AccessScope scope, UUID staffId) {
        raw(staffId);
        return jdbc.query("select id, amount, from_date, reason, created_at from salary_history where staff_id = :s order by from_date desc, created_at desc",
                new MapSqlParameterSource("s", staffId), (rs, i) -> new HrDtos.SalaryHistoryView((UUID) rs.getObject(1), rs.getBigDecimal(2).toPlainString(),
                        rs.getDate(3).toLocalDate(), rs.getString(4), rs.getTimestamp(5).toInstant()));
    }

    /** Personal ACTIVE de una sede visible, para selectores (solicitudes de permiso, corridas de planilla). */
    @Transactional(readOnly = true)
    public List<HrDtos.StaffView> activeOptions(AuthenticatedActor actor, AccessScope scope, UUID branchId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("b", branchId).addValue("org", scope.organizationId());
        boolean h = hasSensitive(actor);
        return jdbc.query(SELECT + " from staff_member s left join branch b on b.id = s.branch_id join person p on p.id = s.person_id" + JOINS
                        + " where s.organization_id = :org and s.branch_id = :b and s.status = 'ACTIVE' order by p.first_name, p.last_name", ps, (rs, i) -> view(rs, h));
    }

    // ---------------------------------------------------------------- helpers

    private UUID validateBranch(AccessScope scope, UUID branchId) {
        if (branchId == null || !scope.canSeeBranch(branchId)) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
        }
        return branchId;
    }

    private static String validContractType(String s) {
        String t = s == null ? null : s.trim().toUpperCase();
        if (t == null || !CONTRACT_TYPES.contains(t)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo de contrato");
        }
        return t;
    }

    private static BigDecimal validSalary(String s) {
        BigDecimal v = HrSupport.requiredAmount(s, "sueldo");
        if (v.compareTo(BigDecimal.ZERO) < 0) {
            throw new Exceptions("error.hr.salaryInvalid", HttpStatus.UNPROCESSABLE_ENTITY);                                // [V3]
        }
        return HrSupport.scale2(v);
    }

    private HrDtos.StaffView view(java.sql.ResultSet rs, boolean sensitive) throws java.sql.SQLException {
        java.sql.Date ce = rs.getDate("contract_end");
        java.sql.Date td = rs.getDate("termination_date");
        BigDecimal salary = rs.getBigDecimal("base_salary");
        String bank = support.decryptBank(rs.getString("bank_encrypted"));
        return new HrDtos.StaffView((UUID) rs.getObject("id"), (UUID) rs.getObject("branch_id"), rs.getString("branch_name"), (UUID) rs.getObject("person_id"),
                rs.getString("person_name"), rs.getString("position"), (UUID) rs.getObject("ministry_id"), rs.getString("ministry_name"),
                rs.getString("contract_type"), rs.getDate("hire_date").toLocalDate(), ce == null ? null : ce.toLocalDate(), td == null ? null : td.toLocalDate(),
                rs.getString("termination_reason"), sensitive ? salary.toPlainString() : null, rs.getString("currency"), rs.getString("pay_frequency"),
                sensitive ? bank : null, sensitive, rs.getString("status"), rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }
}
