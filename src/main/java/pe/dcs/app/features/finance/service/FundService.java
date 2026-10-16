package pe.dcs.app.features.finance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
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

import java.sql.Array;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M15 · Fondos (FIN_FUNDS, N2 solo). [V1] code único por organización; RESTRICTED exige allowedCategories no vacío (los demás
 * tipos admiten null = todas). Inactivar solo si no tiene movimientos PENDING que lo referencien (decisión propia: el spec no
 * lo exige, pero dejar un movimiento pendiente "huérfano" sobre un fondo inactivo rompería la aprobación).
 */
@Service
@RequiredArgsConstructor
public class FundService {

    private static final Set<String> TYPES = Set.of("GENERAL", "RESTRICTED", "BUILDING", "MISSIONS", "OTHER");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final String ENTITY = "Fund";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final Clock clock;

    record Row(UUID id, String code, String type, String status) {
    }

    @Transactional(readOnly = true)
    public PageResponse<FinanceDtos.FundView> search(AccessScope scope, FinanceDtos.FundSearch req) {
        FinanceDtos.FundSearch.FundFilters f = req == null || req.filters() == null ? new FinanceDtos.FundSearch.FundFilters(null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId());
        StringBuilder w = new StringBuilder("organization_id = :org");
        if (FinanceSupport.hasText(f.status())) {
            w.append(" and status = :st");
            ps.addValue("st", f.status().trim().toUpperCase());
        }
        if (FinanceSupport.hasText(f.type())) {
            w.append(" and type = :ty");
            ps.addValue("ty", f.type().trim().toUpperCase());
        }
        Long total = jdbc.queryForObject("select count(*) from fin_fund where " + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<FinanceDtos.FundView> rows = jdbc.query("select * from fin_fund where " + w + " order by code limit :lim offset :off", ps, (rs, i) -> view(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public FinanceDtos.FundView get(AccessScope scope, UUID id) {
        return jdbc.query("select * from fin_fund where id = :id and organization_id = :o", new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()),
                (rs, i) -> view(rs)).stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** Fondos ACTIVE de la organización, para selectores (movimientos, presupuestos, promesas). */
    @Transactional(readOnly = true)
    public List<FinanceDtos.FundView> activeOptions(UUID orgId) {
        return jdbc.query("select * from fin_fund where organization_id = :o and status = 'ACTIVE' order by code",
                new MapSqlParameterSource("o", orgId), (rs, i) -> view(rs));
    }

    Row lockRow(AccessScope scope, UUID id) {
        List<Row> r = jdbc.query("select id, code, type, status from fin_fund where id = :id and organization_id = :o for update",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()), (rs, i) -> new Row((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4)));
        if (r.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return r.get(0);
    }

    /** Fondo activo, la moneda que debe coincidir en el movimiento, y las categorías permitidas (null = todas). */
    @Transactional(readOnly = true)
    public FundFacts facts(UUID orgId, UUID fundId) {
        List<FundFacts> r = jdbc.query("select id, status, currency, allowed_categories from fin_fund where id = :id and organization_id = :o",
                new MapSqlParameterSource("id", fundId).addValue("o", orgId),
                (rs, i) -> new FundFacts((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), toList(rs.getArray(4))));
        if (r.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return r.get(0);
    }

    public record FundFacts(UUID id, String status, String currency, List<String> allowedCategories) {
    }

    @Transactional
    public FinanceDtos.FundView create(AuthenticatedActor actor, AccessScope scope, FinanceDtos.FundRequest r) {
        authz.require(actor, FinanceSupport.MOD_FUNDS, Action.C);
        String code = FinanceSupport.trim(r == null ? null : r.code(), 20, "código");
        if (code == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "código");
        }
        String name = FinanceSupport.trim(r.name(), 120, "nombre");
        if (name == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        String type = r.type() == null ? null : r.type().trim().toUpperCase();
        if (type == null || !TYPES.contains(type)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
        }
        List<String> allowed = validateAllowed(type, r.allowedCategories());
        String currency = FinanceSupport.hasText(r.currency()) ? r.currency().trim().toUpperCase() : "PEN";
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into fin_fund (id, organization_id, code, name, type, allowed_categories, currency, status, created_at, created_by)"
                            + " values (:id, :o, :c, :n, :ty, cast(:ac as text[]), :cu, 'ACTIVE', :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("c", code.toUpperCase()).addValue("n", name)
                            .addValue("ty", type).addValue("ac", toPgArray(allowed))
                            .addValue("cu", currency).addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()));
        } catch (DuplicateKeyException e) {
            throw new Exceptions("error.common.duplicate", HttpStatus.CONFLICT, "código");                                 // [V1]
        }
        audit.record(new AuditService.Command(FinanceSupport.MOD_FUNDS, "CREATE", ENTITY, id, scope.organizationId(), null, Map.of("code", code)));
        return get(scope, id);
    }

    @Transactional
    public FinanceDtos.FundView update(AuthenticatedActor actor, AccessScope scope, UUID id, FinanceDtos.FundRequest r) {
        authz.require(actor, FinanceSupport.MOD_FUNDS, Action.E);
        lockRow(scope, id);
        String name = FinanceSupport.trim(r.name(), 120, "nombre");
        if (name == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        String type = r.type() == null ? null : r.type().trim().toUpperCase();
        if (type == null || !TYPES.contains(type)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
        }
        List<String> allowed = validateAllowed(type, r.allowedCategories());
        String currency = FinanceSupport.hasText(r.currency()) ? r.currency().trim().toUpperCase() : "PEN";
        int n = jdbc.update("update fin_fund set name = :n, type = :ty, allowed_categories = cast(:ac as text[]), currency = :cu, updated_at = :at,"
                        + " updated_by = :by, version = version + 1 where id = :id and (cast(:v as bigint) is null or version = :v)",
                new MapSqlParameterSource("n", name).addValue("ty", type).addValue("ac", toPgArray(allowed))
                        .addValue("cu", currency).addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id)
                        .addValue("v", r.version()));
        if (n == 0) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(FinanceSupport.MOD_FUNDS, "UPDATE", ENTITY, id, scope.organizationId(), null, Map.of()));
        return get(scope, id);
    }

    @Transactional
    public FinanceDtos.FundView setStatus(AuthenticatedActor actor, AccessScope scope, UUID id, String statusRaw) {
        authz.require(actor, FinanceSupport.MOD_FUNDS, Action.E);
        Row f = lockRow(scope, id);
        String status = statusRaw == null ? null : statusRaw.trim().toUpperCase();
        if (status == null || !STATUSES.contains(status)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
        }
        if ("INACTIVE".equals(status) && "ACTIVE".equals(f.status())) {
            Integer pending = jdbc.queryForObject("select count(*) from fin_movement where fund_id = :id and status = 'PENDING'",
                    new MapSqlParameterSource("id", id), Integer.class);
            if (pending != null && pending > 0) {
                throw new Exceptions("error.common.hasDependencies", HttpStatus.CONFLICT);
            }
        }
        jdbc.update("update fin_fund set status = :s, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("s", status).addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
        audit.record(new AuditService.Command(FinanceSupport.MOD_FUNDS, "STATUS", ENTITY, id, scope.organizationId(), null, Map.of("status", status)));
        return get(scope, id);
    }

    private List<String> validateAllowed(String type, List<String> allowedCategories) {
        if (allowedCategories == null || allowedCategories.isEmpty()) {
            if ("RESTRICTED".equals(type)) {
                throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "categorías permitidas");
            }
            return null;
        }
        Set<String> all = new java.util.HashSet<>(FinanceSupport.INCOME_CATEGORIES);
        all.addAll(FinanceSupport.EXPENSE_CATEGORIES);
        List<String> out = new ArrayList<>();
        for (String c : allowedCategories) {
            String u = c == null ? null : c.trim().toUpperCase();
            if (u == null || !all.contains(u)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "categoría");
            }
            out.add(u);
        }
        return out;
    }

    /** Literal de arreglo de Postgres ("{A,B}"), o null: nuestros códigos de categoría son palabras simples, sin comas ni llaves que escapar. */
    private static String toPgArray(List<String> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return "{" + String.join(",", items) + "}";
    }

    private static List<String> toList(Array array) {
        try {
            if (array == null) {
                return null;
            }
            Object o = array.getArray();
            List<String> out = new ArrayList<>();
            for (Object item : (Object[]) o) {
                out.add((String) item);
            }
            return out.isEmpty() ? null : out;
        } catch (java.sql.SQLException e) {
            return null;
        }
    }

    private static FinanceDtos.FundView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FinanceDtos.FundView((UUID) rs.getObject("id"), rs.getString("code"), rs.getString("name"), rs.getString("type"),
                toList(rs.getArray("allowed_categories")), rs.getString("currency"), rs.getString("status"), rs.getTimestamp("created_at").toInstant(),
                rs.getLong("version"));
    }
}
