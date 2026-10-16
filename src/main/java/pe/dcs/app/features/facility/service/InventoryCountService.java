package pe.dcs.app.features.facility.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.facility.dto.FacilityDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M16 · Conteo físico: sesión (DRAFT) con una línea por activo/consumible de la sede (expected = saldo actual al abrir) →
 * cargar lo contado → cerrar: cada diferencia genera un {@code inventory_movement} ADJUSTMENT auditado que deja el saldo en
 * lo contado, y la sesión pasa a CLOSED [M16-T07].
 */
@Service
@RequiredArgsConstructor
public class InventoryCountService {

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final FacilitySupport support;
    private final InventoryMovementService movements;
    private final AuditService audit;
    private final Clock clock;

    @Transactional
    public FacilityDtos.CountView start(AuthenticatedActor actor, AccessScope scope, FacilityDtos.CountStartRequest r) {
        authz.require(actor, FacilitySupport.MOD_INVENTORY, Action.C);
        if (r == null || r.branchId() == null || !scope.canSeeBranch(r.branchId())) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
        }
        LocalDate date = r.countDate() == null ? LocalDate.now(support.zoneOf(r.branchId(), scope.organizationId())) : r.countDate();
        UUID id = UUID.randomUUID();
        jdbc.update("insert into inventory_count (id, organization_id, branch_id, count_date, status, created_at, created_by)"
                        + " values (:id, :o, :b, :d, 'DRAFT', :at, :by)",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", r.branchId()).addValue("d", date)
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()));
        String itemFilter = r.itemIds() == null || r.itemIds().isEmpty() ? "" : " and id in (:items)";
        MapSqlParameterSource ips = new MapSqlParameterSource("b", r.branchId());
        if (!itemFilter.isEmpty()) {
            ips.addValue("items", r.itemIds());
        }
        List<Object[]> items = jdbc.query("select id, quantity from inventory_item where branch_id = :b and status = 'ACTIVE'" + itemFilter, ips,
                (rs, i) -> new Object[]{rs.getObject(1), rs.getBigDecimal(2)});
        for (Object[] it : items) {
            jdbc.update("insert into inventory_count_line (id, count_id, item_id, expected) values (:id, :c, :it, :ex)",
                    new MapSqlParameterSource("id", UUID.randomUUID()).addValue("c", id).addValue("it", it[0]).addValue("ex", it[1]));
        }
        audit.record(new AuditService.Command(FacilitySupport.MOD_INVENTORY, "COUNT_START", "InventoryCount", id, scope.organizationId(), r.branchId(),
                Map.of("items", items.size())));
        return get(scope, id);
    }

    @Transactional(readOnly = true)
    public FacilityDtos.CountView get(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String vis = FacilitySupport.visibleByBranch(scope, ps, "c");
        List<Object[]> head = jdbc.query("select c.id, c.branch_id, c.count_date, c.status, c.closed_at, c.version from inventory_count c where c.id = :id and " + vis,
                ps, (rs, i) -> new Object[]{rs.getObject(1), rs.getObject(2), rs.getDate(3).toLocalDate(), rs.getString(4),
                        rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant(), rs.getLong(6)});
        if (head.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Object[] h = head.get(0);
        List<FacilityDtos.CountLineView> lines = jdbc.query("select l.item_id, it.code, it.name, l.expected, l.counted, l.adjusted, l.note"
                        + " from inventory_count_line l join inventory_item it on it.id = l.item_id where l.count_id = :id order by it.code",
                new MapSqlParameterSource("id", id), (rs, i) -> new FacilityDtos.CountLineView((UUID) rs.getObject(1), rs.getString(2), rs.getString(3),
                        rs.getBigDecimal(4).toPlainString(), rs.getBigDecimal(5) == null ? null : rs.getBigDecimal(5).toPlainString(), rs.getBoolean(6), rs.getString(7)));
        return new FacilityDtos.CountView((UUID) h[0], (UUID) h[1], (LocalDate) h[2], (String) h[3], lines, (java.time.Instant) h[4], (Long) h[5]);
    }

    @Transactional
    public FacilityDtos.CountView enter(AuthenticatedActor actor, AccessScope scope, UUID id, FacilityDtos.CountEnterRequest r) {
        authz.require(actor, FacilitySupport.MOD_INVENTORY, Action.E);
        FacilityDtos.CountView cur = get(scope, id);
        if (!"DRAFT".equals(cur.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, cur.status());
        }
        if (r != null && r.lines() != null) {
            for (FacilityDtos.CountLineEntry e : r.lines()) {
                BigDecimal counted = FacilitySupport.optionalAmount(e.counted(), "contado");
                if (counted != null && counted.signum() < 0) {
                    throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "contado");
                }
                jdbc.update("update inventory_count_line set counted = :c, note = :n where count_id = :cnt and item_id = :it",
                        new MapSqlParameterSource("c", counted).addValue("n", FacilitySupport.trim(e.note(), 300, "nota")).addValue("cnt", id).addValue("it", e.itemId()));
            }
        }
        return get(scope, id);
    }

    @Transactional
    public FacilityDtos.CountView close(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, FacilitySupport.MOD_INVENTORY, Action.E);                                                      // "aprobación de admin": ya exige E
        FacilityDtos.CountView cur = get(scope, id);
        if (!"DRAFT".equals(cur.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, cur.status());
        }
        List<UUID> adjusted = new ArrayList<>();
        for (FacilityDtos.CountLineView line : cur.lines()) {
            if (line.counted() == null) {
                continue;
            }
            BigDecimal expected = new BigDecimal(line.expected());
            BigDecimal counted = new BigDecimal(line.counted());
            int cmp = counted.compareTo(expected);
            if (cmp == 0) {
                continue;
            }
            BigDecimal diff = counted.subtract(expected).abs();
            String note = "Ajuste por conteo físico" + (line.note() == null ? "" : ": " + line.note());
            movements.recordInternal(scope, line.itemId(), cmp > 0 ? "IN" : "OUT", "ADJUSTMENT", diff, cur.countDate(), null, null, note, null);
            jdbc.update("update inventory_count_line set adjusted = true where count_id = :c and item_id = :it",
                    new MapSqlParameterSource("c", id).addValue("it", line.itemId()));
            adjusted.add(line.itemId());
        }
        jdbc.update("update inventory_count set status = 'CLOSED', closed_at = :at, closed_by = :by, updated_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
        audit.record(new AuditService.Command(FacilitySupport.MOD_INVENTORY, "COUNT_CLOSE", "InventoryCount", id, scope.organizationId(), cur.branchId(),
                Map.of("adjusted", adjusted.size())));
        return get(scope, id);
    }
}
