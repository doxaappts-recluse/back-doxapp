package pe.dcs.app.features.facility.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
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
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M16 · Ítems de inventario (INVENTORY): activos y consumibles. [V9] código único por sede. [V14] con movimientos no se borra.
 * [V13] baja (RETIRED) exige que no tenga una asignación activa. Traspaso entre sedes [V2][M16-T11]: solo ORG_ADMIN, OUT+IN
 * atómicos con el mismo {@code transferGroupId}.
 */
@Service
@RequiredArgsConstructor
public class InventoryItemService {

    private static final Set<String> KINDS = Set.of("ASSET", "CONSUMABLE");
    private static final Set<String> CONDITIONS = Set.of("NEW", "GOOD", "FAIR", "POOR", "BROKEN", "RETIRED");
    private static final String ENTITY = "InventoryItem";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final FacilitySupport support;
    private final InventoryMovementService movements;
    private final AuditService audit;
    private final Clock clock;

    record Row(UUID id, UUID branchId, String code, String kind, BigDecimal quantity, String status) {
    }

    @Transactional(readOnly = true)
    public PageResponse<FacilityDtos.ItemView> search(AccessScope scope, FacilityDtos.ItemSearch req) {
        FacilityDtos.ItemSearch.ItemFilters f = req == null || req.filters() == null ? new FacilityDtos.ItemSearch.ItemFilters(null, null, null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(FacilitySupport.visibleByBranch(scope, ps, "i"));
        if (f.branchId() != null) {
            w.append(" and i.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (FacilitySupport.hasText(f.kind())) {
            w.append(" and i.kind = :fk");
            ps.addValue("fk", f.kind().trim().toUpperCase());
        }
        if (FacilitySupport.hasText(f.categoryCode())) {
            w.append(" and i.category_code = :fc");
            ps.addValue("fc", f.categoryCode().trim().toUpperCase());
        }
        if (FacilitySupport.hasText(f.status())) {
            w.append(" and i.status = :fs");
            ps.addValue("fs", f.status().trim().toUpperCase());
        }
        if (FacilitySupport.hasText(f.q())) {
            w.append(" and (lower(i.name) like :q or lower(i.code) like :q)");
            ps.addValue("q", "%" + f.q().trim().toLowerCase() + "%");
        }
        if (Boolean.TRUE.equals(f.lowStock())) {
            w.append(" and i.min_stock is not null and i.quantity <= i.min_stock");
        }
        String from = " from inventory_item i left join branch b on b.id = i.branch_id where ";
        Long total = jdbc.queryForObject("select count(*)" + from + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<FacilityDtos.ItemView> rows = jdbc.query(SELECT + from + w + " order by b.name, i.code limit :lim offset :off", ps, (rs, i) -> view(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    private static final String SELECT = "select i.*, b.name as branch_name";

    @Transactional(readOnly = true)
    public FacilityDtos.ItemView get(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String vis = FacilitySupport.visibleByBranch(scope, ps, "i");
        return jdbc.query(SELECT + " from inventory_item i left join branch b on b.id = i.branch_id where i.id = :id and " + vis, ps, (rs, i) -> view(rs))
                .stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    Row raw(UUID id) {
        List<Row> r = jdbc.query("select id, branch_id, code, kind, quantity, status from inventory_item where id = :id", new MapSqlParameterSource("id", id),
                (rs, i) -> new Row((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getString(6)));
        if (r.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return r.get(0);
    }

    Row lockRaw(UUID id) {
        raw(id);
        return jdbc.query("select id, branch_id, code, kind, quantity, status from inventory_item where id = :id for update", new MapSqlParameterSource("id", id),
                (rs, i) -> new Row((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getString(6))).get(0);
    }

    @Transactional
    public FacilityDtos.ItemView create(AuthenticatedActor actor, AccessScope scope, FacilityDtos.ItemRequest r) {
        authz.require(actor, FacilitySupport.MOD_INVENTORY, Action.C);
        UUID branchId = validateBranch(scope, r == null ? null : r.branchId());
        String code = FacilitySupport.trim(r.code(), 40, "código");
        if (code == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "código");
        }
        String name = FacilitySupport.trim(r.name(), 150, "nombre");
        if (name == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        String kind = r.kind() == null ? null : r.kind().trim().toUpperCase();
        if (kind == null || !KINDS.contains(kind)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
        }
        String condition = validCondition(r.condition());
        BigDecimal cost = FacilitySupport.optionalAmount(r.cost(), "costo");
        if (cost != null && cost.signum() < 0) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "costo");                                  // [V12]
        }
        BigDecimal qty = FacilitySupport.optionalAmount(r.initialQuantity(), "cantidad");
        if (qty == null) {
            qty = BigDecimal.ZERO;
        }
        if (qty.signum() < 0) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "cantidad");                               // [V10]
        }
        BigDecimal minStock = FacilitySupport.optionalAmount(r.minStock(), "stock mínimo");
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into inventory_item (id, organization_id, branch_id, code, name, category_code, kind, unit, quantity, min_stock, location,"
                            + " acquisition_date, cost, currency, condition, serial_no, status, created_at, created_by) values (:id, :o, :b, :c, :n, :cat,"
                            + " :k, :u, :q, :ms, :loc, :ad, :cost, :cu, :cond, :sn, 'ACTIVE', :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", branchId).addValue("c", code.toUpperCase())
                            .addValue("n", name).addValue("cat", blank(r.categoryCode())).addValue("k", kind)
                            .addValue("u", FacilitySupport.hasText(r.unit()) ? r.unit().trim() : "UNIDAD").addValue("q", qty).addValue("ms", minStock)
                            .addValue("loc", FacilitySupport.trim(r.location(), 150, "ubicación")).addValue("ad", r.acquisitionDate())
                            .addValue("cost", cost).addValue("cu", FacilitySupport.hasText(r.currency()) ? r.currency().trim().toUpperCase() : "PEN")
                            .addValue("cond", condition).addValue("sn", FacilitySupport.trim(r.serialNo(), 80, "serie"))
                            .addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()));
        } catch (DuplicateKeyException e) {
            throw new Exceptions("error.inventory.codeTaken", HttpStatus.CONFLICT);                                        // [V9]
        }
        if (qty.signum() > 0) {
            movements.recordInternal(scope, id, "IN", "PURCHASE", qty, r.acquisitionDate() == null ? java.time.LocalDate.now() : r.acquisitionDate(),
                    cost, null, "Saldo inicial", null);
        }
        audit.record(new AuditService.Command(FacilitySupport.MOD_INVENTORY, "CREATE", ENTITY, id, scope.organizationId(), branchId, Map.of("code", code)));
        return get(scope, id);
    }

    @Transactional
    public FacilityDtos.ItemView update(AuthenticatedActor actor, AccessScope scope, UUID id, FacilityDtos.ItemRequest r) {
        authz.require(actor, FacilitySupport.MOD_INVENTORY, Action.E);
        Row cur = lockRaw(id);
        if (!scope.canSeeBranch(cur.branchId())) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        String name = FacilitySupport.trim(r.name(), 150, "nombre");
        if (name == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        String condition = validCondition(r.condition());
        BigDecimal cost = FacilitySupport.optionalAmount(r.cost(), "costo");
        if (cost != null && cost.signum() < 0) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "costo");
        }
        BigDecimal minStock = FacilitySupport.optionalAmount(r.minStock(), "stock mínimo");
        int n = jdbc.update("update inventory_item set name = :n, category_code = :cat, min_stock = :ms, location = :loc, acquisition_date = :ad, cost = :cost,"
                        + " currency = :cu, condition = :cond, serial_no = :sn, updated_at = :at, updated_by = :by, version = version + 1"
                        + " where id = :id and (cast(:v as bigint) is null or version = :v)",
                new MapSqlParameterSource("n", name).addValue("cat", blank(r.categoryCode())).addValue("ms", minStock)
                        .addValue("loc", FacilitySupport.trim(r.location(), 150, "ubicación")).addValue("ad", r.acquisitionDate()).addValue("cost", cost)
                        .addValue("cu", FacilitySupport.hasText(r.currency()) ? r.currency().trim().toUpperCase() : "PEN").addValue("cond", condition)
                        .addValue("sn", FacilitySupport.trim(r.serialNo(), 80, "serie")).addValue("at", Timestamp.from(clock.instant()))
                        .addValue("by", scope.personId()).addValue("id", id).addValue("v", r.version()));
        if (n == 0) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(FacilitySupport.MOD_INVENTORY, "UPDATE", ENTITY, id, scope.organizationId(), cur.branchId(), Map.of()));
        return get(scope, id);
    }

    /** Baja (RETIRED): exige que no tenga una asignación activa [V13]. */
    @Transactional
    public FacilityDtos.ItemView retire(AuthenticatedActor actor, AccessScope scope, UUID id, String note) {
        authz.require(actor, FacilitySupport.MOD_INVENTORY, Action.S);
        Row cur = lockRaw(id);
        if (!scope.canSeeBranch(cur.branchId())) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Integer active = jdbc.queryForObject("select count(*) from inventory_assignment where item_id = :id and status in ('ASSIGNED','OVERDUE')",
                new MapSqlParameterSource("id", id), Integer.class);
        if (active != null && active > 0) {
            throw new Exceptions("error.inventory.retireAssigned", HttpStatus.CONFLICT);                                    // [V13]
        }
        jdbc.update("update inventory_item set status = 'RETIRED', condition = 'RETIRED', updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
        audit.record(new AuditService.Command(FacilitySupport.MOD_INVENTORY, "RETIRE", ENTITY, id, scope.organizationId(), cur.branchId(), Map.of("note", note)));
        return get(scope, id);
    }

    @Transactional
    public void delete(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, FacilitySupport.MOD_INVENTORY, Action.D);
        Row cur = lockRaw(id);
        if (!scope.canSeeBranch(cur.branchId())) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Integer n = jdbc.queryForObject("select count(*) from inventory_movement where item_id = :id", new MapSqlParameterSource("id", id), Integer.class);
        if (n != null && n > 0) {
            throw new Exceptions("error.inventory.hasMovements", HttpStatus.CONFLICT);                                      // [V14]
        }
        jdbc.update("delete from inventory_item where id = :id", new MapSqlParameterSource("id", id));
        audit.record(new AuditService.Command(FacilitySupport.MOD_INVENTORY, "DELETE", ENTITY, id, scope.organizationId(), cur.branchId(), Map.of()));
    }

    /** [V2][M16-T11] Traspaso entre sedes: solo ORG_ADMIN (T no está en BRANCH_ADMIN_CAPS, pero se revalida el rol por si acaso). */
    @Transactional
    public FacilityDtos.ItemView transfer(AuthenticatedActor actor, AccessScope scope, UUID id, UUID toBranchId, BigDecimal quantity, String note) {
        authz.require(actor, FacilitySupport.MOD_INVENTORY, Action.T);
        if (scope.role() != RoleType.ORG_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);                                           // [M16-T11]
        }
        Row cur = lockRaw(id);
        if (toBranchId == null || toBranchId.equals(cur.branchId()) || !support.branchActive(scope.organizationId(), toBranchId)) {
            throw new Exceptions("error.inventory.transferInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (quantity == null || quantity.signum() <= 0 || quantity.compareTo(cur.quantity()) > 0) {
            throw new Exceptions("error.inventory.negativeStock", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        UUID transferGroup = UUID.randomUUID();
        movements.recordInternal(scope, id, "OUT", "TRANSFER", quantity, java.time.LocalDate.now(support.zoneOf(cur.branchId(), scope.organizationId())),
                null, transferGroup, note, null);
        UUID targetId;
        List<UUID> found = jdbc.queryForList("select id from inventory_item where branch_id = :b and code = :c",
                new MapSqlParameterSource("b", toBranchId).addValue("c", cur.code()), UUID.class);
        if (!found.isEmpty()) {
            targetId = found.get(0);
            jdbc.update("update inventory_item set quantity = quantity + :q, updated_at = :at, version = version + 1 where id = :id",
                    new MapSqlParameterSource("q", quantity).addValue("at", Timestamp.from(clock.instant())).addValue("id", targetId));
            movements.recordInternal(scope, targetId, "IN", "TRANSFER", quantity, java.time.LocalDate.now(support.zoneOf(toBranchId, scope.organizationId())),
                    null, transferGroup, note, null);
        } else {
            targetId = UUID.randomUUID();
            FacilityDtos.ItemView src = get(scope, id);
            jdbc.update("insert into inventory_item (id, organization_id, branch_id, code, name, category_code, kind, unit, quantity, min_stock, location,"
                            + " acquisition_date, cost, currency, condition, serial_no, status, created_at, created_by) values (:id, :o, :b, :c, :n, :cat,"
                            + " :k, :u, :q, :ms, null, :ad, :cost, :cu, :cond, :sn, 'ACTIVE', :at, :by)",
                    new MapSqlParameterSource("id", targetId).addValue("o", scope.organizationId()).addValue("b", toBranchId).addValue("c", src.code())
                            .addValue("n", src.name()).addValue("cat", src.categoryCode()).addValue("k", src.kind()).addValue("u", src.unit())
                            .addValue("q", quantity).addValue("ms", src.minStock() == null ? null : new BigDecimal(src.minStock()))
                            .addValue("ad", src.acquisitionDate()).addValue("cost", src.cost() == null ? null : new BigDecimal(src.cost()))
                            .addValue("cu", src.currency()).addValue("cond", src.condition()).addValue("sn", src.serialNo())
                            .addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()));
            movements.recordInternal(scope, targetId, "IN", "TRANSFER", quantity, java.time.LocalDate.now(support.zoneOf(toBranchId, scope.organizationId())),
                    null, transferGroup, note, null);
        }
        audit.record(new AuditService.Command(FacilitySupport.MOD_INVENTORY, "TRANSFER", ENTITY, id, scope.organizationId(), cur.branchId(),
                Map.of("to", toBranchId.toString(), "targetItem", targetId.toString())));
        return get(scope, id);
    }

    // ---------------------------------------------------------------- helpers

    private UUID validateBranch(AccessScope scope, UUID branchId) {
        if (branchId == null || !scope.canSeeBranch(branchId)) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
        }
        return branchId;
    }

    private static String validCondition(String c) {
        if (c == null || c.isBlank()) {
            return null;
        }
        String u = c.trim().toUpperCase();
        if (!CONDITIONS.contains(u)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "condición");
        }
        return u;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim().toUpperCase();
    }

    private static FacilityDtos.ItemView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        BigDecimal cost = rs.getBigDecimal("cost");
        BigDecimal minStock = rs.getBigDecimal("min_stock");
        return new FacilityDtos.ItemView((UUID) rs.getObject("id"), (UUID) rs.getObject("branch_id"), rs.getString("branch_name"), rs.getString("code"),
                rs.getString("name"), rs.getString("category_code"), rs.getString("kind"), rs.getString("unit"), rs.getBigDecimal("quantity").toPlainString(),
                minStock == null ? null : minStock.toPlainString(), rs.getString("location"),
                rs.getDate("acquisition_date") == null ? null : rs.getDate("acquisition_date").toLocalDate(), cost == null ? null : cost.toPlainString(),
                rs.getString("currency"), rs.getString("condition"), rs.getString("serial_no"), rs.getString("photo_key") != null, rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }
}
