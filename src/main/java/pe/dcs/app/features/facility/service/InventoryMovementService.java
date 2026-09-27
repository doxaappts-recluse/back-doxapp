package pe.dcs.app.features.facility.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.facility.dto.FacilityDtos;
import pe.dcs.app.features.finance.service.FinanceSupport;
import pe.dcs.app.features.finance.service.FinancialMovementService;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.security.authz.ContractGate;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M16 · Movimientos de inventario (entradas/salidas). [V10] cantidades ≥0, una salida nunca deja stock negativo. [V15] alerta de
 * stock mínimo única al cruzar hacia abajo (se rearma si vuelve a subir). [D2, ver V27] una compra (IN/PURCHASE) con costo genera
 * un egreso PENDING en M15 (FIN_MOVEMENTS) cuando ese módulo está contratado, enlazado por {@code source_ref=INVENTORY} +
 * {@code source_id}=id de este movimiento; reintentar no duplica [M16-T12] gracias al índice único de V27.
 */
@Service
@RequiredArgsConstructor
public class InventoryMovementService {

    private static final Set<String> IN_REASONS = Set.of("PURCHASE", "DONATION", "ADJUSTMENT");
    private static final Set<String> OUT_REASONS = Set.of("CONSUMPTION", "LOSS", "ADJUSTMENT");

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final FacilitySupport support;
    private final NotificationService notifications;
    private final ContractGate contractGate;
    private final AuditService audit;
    private final Clock clock;
    private final org.springframework.beans.factory.ObjectProvider<FinancialMovementService> financeProvider;

    @Transactional(readOnly = true)
    public List<FacilityDtos.MovementView> byItem(AccessScope scope, UUID itemId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", itemId);
        String vis = FacilitySupport.visibleByBranch(scope, ps, "it");
        Integer n = jdbc.queryForObject("select count(*) from inventory_item it where it.id = :id and " + vis, ps, Integer.class);
        if (n == null || n == 0) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return jdbc.query("select m.*, it.name as item_name from inventory_movement m join inventory_item it on it.id = m.item_id"
                        + " where m.item_id = :id order by m.movement_date desc, m.created_at desc", new MapSqlParameterSource("id", itemId), (rs, i) -> view(rs));
    }

    @Transactional
    public FacilityDtos.MovementView record(AuthenticatedActor actor, AccessScope scope, FacilityDtos.MovementRequest r) {
        authz.require(actor, FacilitySupport.MOD_INVENTORY, Action.C);
        if (r == null || r.itemId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "activo");
        }
        InventoryItemService.Row item = lockVisible(scope, r.itemId());
        String type = r.type() == null ? null : r.type().trim().toUpperCase();
        if (!"IN".equals(type) && !"OUT".equals(type)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
        }
        String reason = r.reason() == null ? null : r.reason().trim().toUpperCase();
        Set<String> valid = "IN".equals(type) ? IN_REASONS : OUT_REASONS;
        if (reason == null || !valid.contains(reason)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "motivo");
        }
        BigDecimal qty = FacilitySupport.requiredAmount(r.quantity(), "cantidad");
        if (qty.signum() <= 0) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "cantidad");                               // [V10]
        }
        LocalDate date = r.movementDate() == null ? LocalDate.now(support.zoneOf(item.branchId(), scope.organizationId())) : r.movementDate();
        BigDecimal unitCost = FacilitySupport.optionalAmount(r.unitCost(), "costo unitario");
        Applied applied = applyAndInsert(scope, item, type, reason, qty, date, unitCost, null, FacilitySupport.trim(r.note(), 300, "nota"), r.fundId(),
                scope.personId());
        audit.record(new AuditService.Command(FacilitySupport.MOD_INVENTORY, "MOVEMENT", "InventoryMovement", applied.movementId(), scope.organizationId(),
                item.branchId(), Map.of("item", item.code(), "type", type, "reason", reason)));
        return byId(applied.movementId());
    }

    /** Uso interno de otros servicios del módulo (alta con saldo inicial, traspaso): sin control de autorización propio (ya lo hizo el llamador). */
    UUID recordInternal(AccessScope scope, UUID itemId, String type, String reason, BigDecimal quantity, LocalDate date, BigDecimal unitCost,
                        UUID transferGroupId, String note, UUID fundId) {
        InventoryItemService.Row item = jdbc.query("select id, branch_id, code, kind, quantity, status from inventory_item where id = :id for update",
                new MapSqlParameterSource("id", itemId), (rs, i) -> new InventoryItemService.Row((UUID) rs.getObject(1), (UUID) rs.getObject(2),
                        rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getString(6))).get(0);
        return applyAndInsert(scope, item, type, reason, quantity, date, unitCost, transferGroupId, note, fundId, scope.personId()).movementId();
    }

    private record Applied(UUID movementId, UUID financialMovementId) {
    }

    private Applied applyAndInsert(AccessScope scope, InventoryItemService.Row item, String type, String reason, BigDecimal qty, LocalDate date,
                                   BigDecimal unitCost, UUID transferGroupId, String note, UUID fundId, UUID by) {
        BigDecimal current = jdbc.queryForObject("select quantity from inventory_item where id = :id for update", new MapSqlParameterSource("id", item.id()), BigDecimal.class);
        BigDecimal updated = "IN".equals(type) ? current.add(qty) : current.subtract(qty);
        if (updated.signum() < 0) {
            throw new Exceptions("error.inventory.negativeStock", HttpStatus.UNPROCESSABLE_ENTITY);                         // [V10][M16-T07]
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("update inventory_item set quantity = :q, updated_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("q", updated).addValue("at", now).addValue("id", item.id()));

        UUID id = UUID.randomUUID();
        UUID finMovementId = null;
        if ("IN".equals(type) && "PURCHASE".equals(reason) && unitCost != null && unitCost.signum() > 0) {
            finMovementId = tryCreateExpense(scope, item, qty, unitCost, date, id, fundId);
        }
        jdbc.update("insert into inventory_movement (id, organization_id, item_id, type, reason, quantity, movement_date, unit_cost, financial_movement_id,"
                        + " transfer_group_id, note, created_at, created_by) values (:id, :o, :item, :ty, :r, :q, :d, :uc, :fin, :tg, :note, :at, :by)",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("item", item.id()).addValue("ty", type).addValue("r", reason)
                        .addValue("q", qty).addValue("d", date).addValue("uc", unitCost).addValue("fin", finMovementId).addValue("tg", transferGroupId)
                        .addValue("note", note).addValue("at", now).addValue("by", by));
        checkLowStock(scope, item.id(), updated);
        return new Applied(id, finMovementId);
    }

    /** [V15] alerta única al cruzar hacia abajo el mínimo; se rearma si vuelve a subir por encima. */
    private void checkLowStock(AccessScope scope, UUID itemId, BigDecimal newQty) {
        Map<String, Object> row = jdbc.queryForMap("select min_stock, low_stock_alerted_at, name, branch_id from inventory_item where id = :id",
                new MapSqlParameterSource("id", itemId));
        BigDecimal minStock = (BigDecimal) row.get("min_stock");
        if (minStock == null) {
            return;
        }
        boolean low = newQty.compareTo(minStock) <= 0;
        boolean alreadyAlerted = row.get("low_stock_alerted_at") != null;
        if (low && !alreadyAlerted) {
            jdbc.update("update inventory_item set low_stock_alerted_at = :at where id = :id",
                    new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("id", itemId));
            List<UUID> to = notifications.branchAdmins(scope.organizationId(), (UUID) row.get("branch_id"));
            notifications.toPersons(NotificationType.INVENTORY_LOW_STOCK, scope.organizationId(), to,
                    Map.of("item", String.valueOf(row.get("name")), "min", minStock.toPlainString()), "/app/inventory/items/" + itemId, "lowstock:" + itemId);
        } else if (!low && alreadyAlerted) {
            jdbc.update("update inventory_item set low_stock_alerted_at = null where id = :id", new MapSqlParameterSource("id", itemId));
        }
    }

    /** [D2] Egreso PENDING en M15 por una compra con costo; idempotente por (source_ref, source_id) [M16-T12]. */
    private UUID tryCreateExpense(AccessScope scope, InventoryItemService.Row item, BigDecimal qty, BigDecimal unitCost, LocalDate date,
                                  UUID movementId, UUID fundId) {
        if (!contractGate.enabled(scope.organizationId(), FinanceSupport.MOD_MOVEMENTS)) {
            return null;
        }
        if (fundId == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "fondo");
        }
        BigDecimal amount = qty.multiply(unitCost);
        FinancialMovementService finance = financeProvider.getObject();
        String description = "Compra de inventario (" + item.code() + ")";
        return finance.createFromSource(scope.organizationId(), item.branchId(), date, "SUPPLIES", fundId, amount, description, "INVENTORY", movementId, scope.personId());
    }

    private InventoryItemService.Row lockVisible(AccessScope scope, UUID itemId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", itemId);
        String vis = FacilitySupport.visibleByBranch(scope, ps, "i");
        Integer n = jdbc.queryForObject("select count(*) from inventory_item i where i.id = :id and " + vis, ps, Integer.class);
        if (n == null || n == 0) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return jdbc.query("select id, branch_id, code, kind, quantity, status from inventory_item where id = :id for update",
                new MapSqlParameterSource("id", itemId), (rs, i) -> new InventoryItemService.Row((UUID) rs.getObject(1), (UUID) rs.getObject(2),
                        rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getString(6))).get(0);
    }

    private FacilityDtos.MovementView byId(UUID id) {
        return jdbc.query("select m.*, it.name as item_name from inventory_movement m join inventory_item it on it.id = m.item_id where m.id = :id",
                new MapSqlParameterSource("id", id), (rs, i) -> view(rs)).get(0);
    }

    private static FacilityDtos.MovementView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        BigDecimal unitCost = rs.getBigDecimal("unit_cost");
        return new FacilityDtos.MovementView((UUID) rs.getObject("id"), (UUID) rs.getObject("item_id"), rs.getString("item_name"), rs.getString("type"),
                rs.getString("reason"), rs.getBigDecimal("quantity").toPlainString(), rs.getDate("movement_date").toLocalDate(),
                unitCost == null ? null : unitCost.toPlainString(), (UUID) rs.getObject("financial_movement_id"), (UUID) rs.getObject("transfer_group_id"),
                rs.getString("note"), rs.getTimestamp("created_at").toInstant());
    }
}
