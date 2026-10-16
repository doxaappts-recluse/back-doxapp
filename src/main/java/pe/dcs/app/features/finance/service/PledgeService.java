package pe.dcs.app.features.finance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.finance.dto.FinanceDtos;
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
 * M15 · Promesas de ofrenda (FIN_DONORS) [V18: amount&gt;0, endDate&gt;=startDate ya en el CHECK de BD]. Transición manual
 * ACTIVE→FULFILLED/CANCELLED con la acción E (edición de estado): no hay un flujo de aprobación aquí, así que se reutiliza E
 * en vez de S/A (decisión propia: FIN_DONORS no declara S en la migración, y E es la acción de edición general del módulo).
 */
@Service
@RequiredArgsConstructor
public class PledgeService {

    private static final Set<String> FREQUENCIES = Set.of("ONE_TIME", "WEEKLY", "MONTHLY", "YEARLY");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "FULFILLED", "CANCELLED");
    private static final String ENTITY = "Pledge";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final DonorService donors;
    private final FundService funds;
    private final AuditService audit;
    private final Clock clock;

    record Row(UUID id, String status) {
    }

    @Transactional(readOnly = true)
    public PageResponse<FinanceDtos.PledgeView> search(AccessScope scope, FinanceDtos.PledgeSearch req) {
        FinanceDtos.PledgeSearch.PledgeFilters f = req == null || req.filters() == null ? new FinanceDtos.PledgeSearch.PledgeFilters(null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId());
        StringBuilder w = new StringBuilder("pl.organization_id = :org");
        if (f.donorId() != null) {
            w.append(" and pl.donor_id = :fd");
            ps.addValue("fd", f.donorId());
        }
        if (f.fundId() != null) {
            w.append(" and pl.fund_id = :ff");
            ps.addValue("ff", f.fundId());
        }
        if (FinanceSupport.hasText(f.status())) {
            w.append(" and pl.status = :fs");
            ps.addValue("fs", f.status().trim().toUpperCase());
        }
        String from = " from fin_pledge pl join fin_donor d on d.id = pl.donor_id left join person p on p.id = d.person_id join fin_fund fu on fu.id = pl.fund_id where ";
        Long total = jdbc.queryForObject("select count(*)" + from + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<FinanceDtos.PledgeView> rows = jdbc.query("select pl.*, coalesce(d.external_name, trim(p.first_name || ' ' || p.last_name)) as donor_name, fu.name as fund_name"
                + from + w + " order by pl.start_date desc limit :lim offset :off", ps, (rs, i) -> view(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public FinanceDtos.PledgeView get(AccessScope scope, UUID id) {
        return jdbc.query("select pl.*, coalesce(d.external_name, trim(p.first_name || ' ' || p.last_name)) as donor_name, fu.name as fund_name"
                        + " from fin_pledge pl join fin_donor d on d.id = pl.donor_id left join person p on p.id = d.person_id join fin_fund fu on fu.id = pl.fund_id"
                        + " where pl.id = :id and pl.organization_id = :o", new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()),
                (rs, i) -> view(rs)).stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private Row lock(AccessScope scope, UUID id) {
        get(scope, id);
        return jdbc.query("select id, status from fin_pledge where id = :id for update", new MapSqlParameterSource("id", id),
                (rs, i) -> new Row((UUID) rs.getObject(1), rs.getString(2))).get(0);
    }

    @Transactional
    public FinanceDtos.PledgeView create(AuthenticatedActor actor, AccessScope scope, FinanceDtos.PledgeRequest r) {
        authz.require(actor, FinanceSupport.MOD_DONORS, Action.C);
        if (r.donorId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "donante");
        }
        donors.assertExists(scope.organizationId(), r.donorId());
        if (r.fundId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "fondo");
        }
        funds.facts(scope.organizationId(), r.fundId());
        String freq = r.frequency() == null ? null : r.frequency().trim().toUpperCase();
        if (freq == null || !FREQUENCIES.contains(freq)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "frecuencia");
        }
        BigDecimal amount = FinanceSupport.requiredAmount(r.amount(), "monto");
        if (amount.signum() <= 0) {
            throw new Exceptions("error.finance.pledgeInvalid", HttpStatus.BAD_REQUEST);                                    // [V18]
        }
        LocalDate start = r.startDate();
        if (start == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "fecha de inicio");
        }
        if (r.endDate() != null && r.endDate().isBefore(start)) {
            throw new Exceptions("error.finance.pledgeInvalid", HttpStatus.BAD_REQUEST);                                    // [V18]
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into fin_pledge (id, organization_id, donor_id, fund_id, amount, frequency, start_date, end_date, status, created_at, created_by)"
                        + " values (:id, :o, :d, :f, :a, :fr, :s, :e, 'ACTIVE', :at, :by)",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("d", r.donorId()).addValue("f", r.fundId())
                        .addValue("a", amount).addValue("fr", freq).addValue("s", start).addValue("e", r.endDate())
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()));
        audit.record(new AuditService.Command(FinanceSupport.MOD_DONORS, "PLEDGE_CREATE", ENTITY, id, scope.organizationId(), null, Map.of("donorId", r.donorId().toString())));
        return get(scope, id);
    }

    @Transactional
    public FinanceDtos.PledgeView setStatus(AuthenticatedActor actor, AccessScope scope, UUID id, String statusRaw) {
        authz.require(actor, FinanceSupport.MOD_DONORS, Action.E);
        Row p = lock(scope, id);
        String status = statusRaw == null ? null : statusRaw.trim().toUpperCase();
        if (status == null || !STATUSES.contains(status)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
        }
        if (!"ACTIVE".equals(p.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, p.status());
        }
        if ("ACTIVE".equals(status)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
        }
        jdbc.update("update fin_pledge set status = :s, version = version + 1 where id = :id",
                new MapSqlParameterSource("s", status).addValue("id", id));
        audit.record(new AuditService.Command(FinanceSupport.MOD_DONORS, "PLEDGE_STATUS", ENTITY, id, scope.organizationId(), null, Map.of("status", status)));
        return get(scope, id);
    }

    private static FinanceDtos.PledgeView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Date end = rs.getDate("end_date");
        return new FinanceDtos.PledgeView((UUID) rs.getObject("id"), (UUID) rs.getObject("donor_id"), rs.getString("donor_name"),
                (UUID) rs.getObject("fund_id"), rs.getString("fund_name"), rs.getBigDecimal("amount").toPlainString(), rs.getString("frequency"),
                rs.getDate("start_date").toLocalDate(), end == null ? null : end.toLocalDate(), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }
}
