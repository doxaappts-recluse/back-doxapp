package pe.dcs.app.features.finance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.dcs.app.features.finance.dto.FinanceDtos;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.storage.FileStorageService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.io.IOException;
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
 * M15 · El corazón del módulo: movimientos (FIN_MOVEMENTS). [D1] el ciclo PENDING→APPROVED/REJECTED/VOIDED vive aquí a mano
 * (no en {@code ApprovalEngine}): la auto-aprobación es apagable por organización y hay un umbral por monto que enruta a
 * ORG_ADMIN, dos reglas que el motor genérico no modela.
 * <p>[V16] {@code SERVICE_FEE} con {@code sourceRef} no nulo no se edita salvo cambio de estado: esta entrega no expone ningún
 * endpoint de edición de movimientos (solo crear/aprobar/rechazar/anular), así que la regla se cumple de forma vacía por ahora;
 * queda como guía para cuando M14/M08 empiecen a generar movimientos automáticos y se necesite un PUT.
 * <p>[V15] Transferencia entre fondos/cuentas: DIFERIDA en esta entrega (opcional en el spec, sin caso de prueba M15-T01..T21
 * que la exija). Mientras no exista, una transferencia se registra a mano como un EXPENSE en el fondo/cuenta de origen y un
 * INCOME equivalente en el de destino, ambos con la misma referencia en {@code description}; el mensaje
 * {@code error.finance.transferUnbalanced} queda definido en el catálogo de mensajes para cuando se implemente.
 */
@Service
@RequiredArgsConstructor
public class FinancialMovementService {

    private static final String ENTITY = "FinancialMovement";
    private static final Set<String> TYPES = Set.of("INCOME", "EXPENSE");
    private static final Set<String> METHODS = Set.of("CASH", "TRANSFER", "CARD", "OTHER");
    private static final Set<String> DONOR_REQUIRED_CATEGORIES = Set.of("TITHE", "DONATION");
    private static final Set<String> RECEIPT_CATEGORIES = Set.of("TITHE", "OFFERING", "DONATION");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999.99");
    private static final int MAX_FILES = 5;
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final FinanceSupport support;
    private final FundService funds;
    private final FinancialAccountService accounts;
    private final DonorService donors;
    private final FinanceRulesService rules;
    private final FiscalPeriodService periods;
    private final BudgetService budgets;
    private final NotificationService notifications;
    private final FileStorageService storage;
    private final AuditService audit;
    private final Clock clock;

    record MRow(UUID id, UUID orgId, UUID branchId, LocalDate movementDate, String type, String category, UUID fundId, UUID accountId,
               BigDecimal amount, String currency, String method, UUID donorId, boolean anonymous, String description, String receiptNo,
               UUID eventId, String sourceRef, UUID cashRegisterId, UUID offeringCountId, String status, UUID submittedBy, long version) {
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<FinanceDtos.MovementSummary> search(AccessScope scope, FinanceDtos.MovementSearch req) {
        FinanceDtos.MovementSearch.MovementFilters f = req == null || req.filters() == null
                ? new FinanceDtos.MovementSearch.MovementFilters(null, null, null, null, null, null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(FinanceSupport.visibleByBranch(scope, ps, "m"));
        if (f.branchId() != null) {
            w.append(" and m.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (f.fundId() != null) {
            w.append(" and m.fund_id = :ff");
            ps.addValue("ff", f.fundId());
        }
        if (f.accountId() != null) {
            w.append(" and m.account_id = :fa");
            ps.addValue("fa", f.accountId());
        }
        if (FinanceSupport.hasText(f.status())) {
            w.append(" and m.status = :fs");
            ps.addValue("fs", f.status().trim().toUpperCase());
        }
        if (FinanceSupport.hasText(f.type())) {
            w.append(" and m.type = :ft");
            ps.addValue("ft", f.type().trim().toUpperCase());
        }
        if (FinanceSupport.hasText(f.category())) {
            w.append(" and m.category = :fc");
            ps.addValue("fc", f.category().trim().toUpperCase());
        }
        if (f.from() != null) {
            w.append(" and m.movement_date >= :df");
            ps.addValue("df", java.sql.Date.valueOf(f.from()));
        }
        if (f.to() != null) {
            w.append(" and m.movement_date <= :dt");
            ps.addValue("dt", java.sql.Date.valueOf(f.to()));
        }
        if (f.donorId() != null) {
            w.append(" and m.donor_id = :fd");
            ps.addValue("fd", f.donorId());
        }
        String from = " from fin_movement m left join branch b on b.id = m.branch_id join fin_fund fu on fu.id = m.fund_id"
                + " left join fin_account a on a.id = m.account_id left join fin_donor d on d.id = m.donor_id left join person p on p.id = d.person_id"
                + " left join person sp on sp.id = m.submitted_by where ";
        Long total = jdbc.queryForObject("select count(*)" + from + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<FinanceDtos.MovementSummary> rows = jdbc.query(SUMMARY + from + w + " order by m.movement_date desc, m.created_at desc limit :lim offset :off",
                ps, (rs, i) -> summary(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    private static final String SUMMARY = "select m.id, m.branch_id, b.name as branch_name, m.movement_date, m.type, m.category, m.fund_id, fu.name as fund_name,"
            + " m.account_id, a.name as account_name, m.amount, m.currency, m.method, m.donor_id,"
            + " coalesce(d.external_name, trim(p.first_name || ' ' || p.last_name)) as donor_name, m.anonymous, m.description, m.receipt_no, m.status,"
            + " m.submitted_by, trim(sp.first_name || ' ' || sp.last_name) as submitted_by_name, m.created_at, m.version";

    private FinanceDtos.MovementSummary summary(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FinanceDtos.MovementSummary((UUID) rs.getObject("id"), (UUID) rs.getObject("branch_id"), rs.getString("branch_name"),
                rs.getDate("movement_date").toLocalDate(), rs.getString("type"), rs.getString("category"), (UUID) rs.getObject("fund_id"),
                rs.getString("fund_name"), (UUID) rs.getObject("account_id"), rs.getString("account_name"), rs.getBigDecimal("amount").toPlainString(),
                rs.getString("currency"), rs.getString("method"), (UUID) rs.getObject("donor_id"), rs.getString("donor_name"), rs.getBoolean("anonymous"),
                rs.getString("description"), rs.getString("receipt_no"), rs.getString("status"), (UUID) rs.getObject("submitted_by"),
                rs.getString("submitted_by_name"), rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }

    @Transactional(readOnly = true)
    public FinanceDtos.MovementDetail get(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String vis = FinanceSupport.visibleByBranch(scope, ps, "m");
        FinanceDtos.MovementSummary s = jdbc.query(SUMMARY + " from fin_movement m left join branch b on b.id = m.branch_id join fin_fund fu on fu.id = m.fund_id"
                        + " left join fin_account a on a.id = m.account_id left join fin_donor d on d.id = m.donor_id left join person p on p.id = d.person_id"
                        + " left join person sp on sp.id = m.submitted_by where m.id = :id and " + vis, ps, (rs, i) -> summary(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        Map<String, Object> extra = jdbc.queryForMap("select void_reason, reversal_of, source_ref, event_id, cash_register_id, offering_count_id from fin_movement where id = :id",
                new MapSqlParameterSource("id", id));
        List<FinanceDtos.AttachmentView> attachments = jdbc.query("select id, filename, uploaded_at from fin_movement_attachment where movement_id = :id order by uploaded_at",
                new MapSqlParameterSource("id", id), (rs, i) -> new FinanceDtos.AttachmentView((UUID) rs.getObject(1), rs.getString(2), rs.getTimestamp(3).toInstant()));
        return new FinanceDtos.MovementDetail(s, (String) extra.get("void_reason"), (UUID) extra.get("reversal_of"), (String) extra.get("source_ref"),
                (UUID) extra.get("event_id"), (UUID) extra.get("cash_register_id"), (UUID) extra.get("offering_count_id"), attachments);
    }

    private MRow load(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String vis = FinanceSupport.visibleByBranch(scope, ps, "m");
        return jdbc.query("select m.* from fin_movement m where m.id = :id and " + vis, ps, (rs, i) -> row(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private MRow lock(AccessScope scope, UUID id) {
        load(scope, id);
        return jdbc.query("select * from fin_movement where id = :id for update", new MapSqlParameterSource("id", id), (rs, i) -> row(rs)).get(0);
    }

    private static MRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MRow((UUID) rs.getObject("id"), (UUID) rs.getObject("organization_id"), (UUID) rs.getObject("branch_id"),
                rs.getDate("movement_date").toLocalDate(), rs.getString("type"), rs.getString("category"), (UUID) rs.getObject("fund_id"),
                (UUID) rs.getObject("account_id"), rs.getBigDecimal("amount"), rs.getString("currency"), rs.getString("method"),
                (UUID) rs.getObject("donor_id"), rs.getBoolean("anonymous"), rs.getString("description"), rs.getString("receipt_no"),
                (UUID) rs.getObject("event_id"), rs.getString("source_ref"), (UUID) rs.getObject("cash_register_id"), (UUID) rs.getObject("offering_count_id"),
                rs.getString("status"), (UUID) rs.getObject("submitted_by"), rs.getLong("version"));
    }

    // ---------------------------------------------------------------- alta

    @Transactional
    public FinanceDtos.MovementDetail create(AuthenticatedActor actor, AccessScope scope, FinanceDtos.MovementRequest r, List<MultipartFile> files) {
        authz.require(actor, FinanceSupport.MOD_MOVEMENTS, Action.C);
        if (r == null || r.branchId() == null || !scope.canSeeBranch(r.branchId())) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
        }
        String type = r.type() == null ? null : r.type().trim().toUpperCase();
        if (!TYPES.contains(type)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
        }
        String category = r.category() == null ? null : r.category().trim().toUpperCase();
        Set<String> validCategories = "INCOME".equals(type) ? FinanceSupport.INCOME_CATEGORIES : FinanceSupport.EXPENSE_CATEGORIES;
        if (category == null || !validCategories.contains(category)) {
            throw new Exceptions("error.finance.categoryTypeMismatch", HttpStatus.UNPROCESSABLE_ENTITY);                    // [V7]
        }
        String method = r.method() == null ? null : r.method().trim().toUpperCase();
        if (!METHODS.contains(method)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "método");
        }
        if (r.movementDate() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "fecha");
        }
        LocalDate today = LocalDate.now(support.zoneOf(r.branchId(), scope.organizationId()));
        if (r.movementDate().isAfter(today)) {
            throw new Exceptions("error.common.futureDate", HttpStatus.BAD_REQUEST);                                        // [V5], [T20]
        }
        periods.assertOpen(scope.organizationId(), r.movementDate());                                                       // [V5]/[V11]: abre o valida OPEN

        BigDecimal amount = FinanceSupport.requiredAmount(r.amount(), "monto");
        if (amount.signum() <= 0 || amount.scale() > 2 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new Exceptions("error.finance.amountInvalid", HttpStatus.BAD_REQUEST);                                    // [V4]
        }

        if (r.fundId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "fondo");
        }
        FundService.FundFacts fund = funds.facts(scope.organizationId(), r.fundId());
        if (!"ACTIVE".equals(fund.status())) {
            throw new Exceptions("error.finance.fundInactive", HttpStatus.UNPROCESSABLE_ENTITY);                            // [V6]
        }
        if (fund.allowedCategories() != null && !fund.allowedCategories().contains(category)) {
            throw new Exceptions("error.finance.categoryNotAllowed", HttpStatus.UNPROCESSABLE_ENTITY);                      // [V6/V7]
        }

        // [V14] el movimiento no tiene un campo de moneda propio en la petición: siempre nace en la moneda del fondo (abajo,
        // al insertar). Lo único que puede desentonar es la cuenta de depósito, así que la comparación real es
        // cuenta.currency vs fondo.currency cuando se indica una cuenta — decisión propia, documentada porque el spec no
        // dice de dónde sale la moneda del movimiento si no se pide en la petición.
        if (r.accountId() != null) {
            FinanceDtos.AccountView acc = accounts.get(scope, r.accountId());
            if (!"ACTIVE".equals(acc.status())) {
                throw new Exceptions("error.common.invalidState", HttpStatus.UNPROCESSABLE_ENTITY, acc.status());
            }
            if (!fund.currency().equalsIgnoreCase(acc.currency())) {
                throw new Exceptions("error.finance.currencyMismatch", HttpStatus.UNPROCESSABLE_ENTITY);                    // [V14]
            }
        }

        boolean anonymous = Boolean.TRUE.equals(r.anonymous());
        UUID donorId = r.donorId();
        if (DONOR_REQUIRED_CATEGORIES.contains(category) && donorId == null && !anonymous) {
            throw new Exceptions("error.finance.donorRequired", HttpStatus.UNPROCESSABLE_ENTITY);                           // [V8]
        }
        if (donorId != null) {
            donors.assertExists(scope.organizationId(), donorId);
        }

        List<AttachmentChecked> checked = checkFiles(files);
        if ("EXPENSE".equals(type)) {
            FinanceRulesService.Row rls = rules.get(scope.organizationId());
            if (amount.compareTo(rls.attachmentThreshold()) >= 0 && checked.isEmpty()) {
                throw new Exceptions("error.finance.attachmentRequired", HttpStatus.UNPROCESSABLE_ENTITY, rls.attachmentThreshold().toPlainString());   // [V9]
            }
            budgets.onExpenseRegistered(scope, r.branchId(), r.fundId(), category, r.movementDate(), amount, rls.overspendBlock());      // [V17]
        }

        UUID cashRegisterId = "CASH".equals(method) ? findOpenRegister(scope, r.branchId(), r.movementDate()) : null;
        String description = FinanceSupport.trim(r.description(), 500, "descripción");
        UUID id = UUID.randomUUID();
        jdbc.update("insert into fin_movement (id, organization_id, branch_id, movement_date, type, category, fund_id, account_id, amount, currency,"
                        + " method, donor_id, anonymous, description, event_id, cash_register_id, status, submitted_by, created_at, created_by)"
                        + " values (:id, :o, :b, :d, :ty, :c, :f, :a, :am, :cu, :m, :don, :an, :desc, :ev, :cr, 'PENDING', :sub, :at, :by)",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", r.branchId()).addValue("d", java.sql.Date.valueOf(r.movementDate()))
                        .addValue("ty", type).addValue("c", category).addValue("f", r.fundId()).addValue("a", r.accountId()).addValue("am", amount)
                        .addValue("cu", fund.currency()).addValue("m", method).addValue("don", donorId).addValue("an", anonymous).addValue("desc", description)
                        .addValue("ev", r.eventId()).addValue("cr", cashRegisterId).addValue("sub", scope.personId()).addValue("at", Timestamp.from(clock.instant()))
                        .addValue("by", scope.personId()));
        saveAttachments(scope, id, checked);
        audit.record(new AuditService.Command(FinanceSupport.MOD_MOVEMENTS, "CREATE", ENTITY, id, scope.organizationId(), r.branchId(),
                Map.of("type", type, "category", category, "amount", amount.toPlainString())));
        return get(scope, id);
    }

    /**
     * M16 [D2] · Egreso PENDING generado por otro módulo (hoy Inventario y Planilla, {@code sourceRef=INVENTORY|PAYROLL_RUN}),
     * sin pasar por {@code authz.require} (el módulo llamante ya validó su propia acción) ni por el adjunto obligatorio [V9]
     * (no hay comprobante todavía; se decide como un movimiento normal más). Idempotente: si ya existe un movimiento con este
     * {@code (sourceRef, sourceId)} (índice único de V27), devuelve su id sin insertar de nuevo [M16-T12]. Sobrecarga histórica
     * que fija {@code type=EXPENSE} y {@code eventId=null} — se mantiene intacta para no tocar las llamadas ya validadas de
     * {@code InventoryMovementService}/{@code PayrollRunService}; delega en la versión de abajo.
     */
    @Transactional
    public UUID createFromSource(UUID orgId, UUID branchId, LocalDate movementDate, String category, UUID fundId, BigDecimal amount, String description,
                                 String sourceRef, UUID sourceId, UUID submittedBy) {
        return createFromSource(orgId, branchId, movementDate, "EXPENSE", category, fundId, amount, description, null, sourceRef, sourceId, submittedBy);
    }

    /**
     * M20+ [V32, cierra la mitad de M14 del D5 de esta clase] · Misma idea que la sobrecarga de arriba, generalizada para
     * ingresos: {@code type} ahora es parte de la firma ({@code EVENT_REGISTRATION} de M14 llama con {@code INCOME}/
     * {@code SERVICE_FEE} y {@code eventId} no nulo para que el movimiento quede enlazado a {@code org_event.id} igual que
     * cualquier otro ingreso de evento) y {@code eventId} es opcional (null para todo lo que no sea un evento). Mismo
     * candado de idempotencia por {@code (sourceRef, sourceId)} y mismas validaciones de fondo activo/categoría permitida.
     */
    @Transactional
    public UUID createFromSource(UUID orgId, UUID branchId, LocalDate movementDate, String type, String category, UUID fundId, BigDecimal amount,
                                 String description, UUID eventId, String sourceRef, UUID sourceId, UUID submittedBy) {
        List<UUID> existing = jdbc.queryForList("select id from fin_movement where source_ref = :sr and source_id = :sid",
                new MapSqlParameterSource("sr", sourceRef).addValue("sid", sourceId), UUID.class);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        periods.assertOpen(orgId, movementDate);
        FundService.FundFacts fund = funds.facts(orgId, fundId);
        if (!"ACTIVE".equals(fund.status())) {
            throw new Exceptions("error.finance.fundInactive", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (fund.allowedCategories() != null && !fund.allowedCategories().contains(category)) {
            throw new Exceptions("error.finance.categoryNotAllowed", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into fin_movement (id, organization_id, branch_id, movement_date, type, category, fund_id, amount, currency, method,"
                            + " anonymous, description, event_id, source_ref, source_id, status, submitted_by, created_at, created_by)"
                            + " values (:id, :o, :b, :d, :ty, :c, :f, :am, :cu, 'OTHER', false, :desc, :ev, :sr, :sid, 'PENDING', :sub, :at, :sub)",
                    new MapSqlParameterSource("id", id).addValue("o", orgId).addValue("b", branchId).addValue("d", java.sql.Date.valueOf(movementDate))
                            .addValue("ty", type).addValue("c", category).addValue("f", fundId).addValue("am", amount).addValue("cu", fund.currency())
                            .addValue("desc", description).addValue("ev", eventId).addValue("sr", sourceRef).addValue("sid", sourceId)
                            .addValue("sub", submittedBy).addValue("at", Timestamp.from(clock.instant())));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // otro hilo insertó primero para el mismo (sourceRef, sourceId): idempotencia por el índice único de V27
            return jdbc.queryForList("select id from fin_movement where source_ref = :sr and source_id = :sid",
                    new MapSqlParameterSource("sr", sourceRef).addValue("sid", sourceId), UUID.class).get(0);
        }
        audit.record(new AuditService.Command(FinanceSupport.MOD_MOVEMENTS, "CREATE", ENTITY, id, orgId, branchId,
                Map.of("sourceRef", sourceRef, "amount", amount.toPlainString())));
        return id;
    }

    /** Caja OPEN de esa sede y fecha: primero la de quien registra; si no tiene una abierta, la única OPEN de esa sede/fecha (si hay más de una, ninguna). */
    private UUID findOpenRegister(AccessScope scope, UUID branchId, LocalDate date) {
        List<UUID> own = jdbc.query("select id from fin_cash_register where branch_id = :b and register_date = :d and opened_by = :p and status = 'OPEN'",
                new MapSqlParameterSource("b", branchId).addValue("d", java.sql.Date.valueOf(date)).addValue("p", scope.personId()), (rs, i) -> (UUID) rs.getObject(1));
        if (!own.isEmpty()) {
            return own.get(0);
        }
        List<UUID> any = jdbc.query("select id from fin_cash_register where branch_id = :b and register_date = :d and status = 'OPEN'",
                new MapSqlParameterSource("b", branchId).addValue("d", java.sql.Date.valueOf(date)), (rs, i) -> (UUID) rs.getObject(1));
        return any.size() == 1 ? any.get(0) : null;
    }

    // ---------------------------------------------------------------- decisión

    @Transactional
    public FinanceDtos.MovementDetail approve(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, FinanceSupport.MOD_MOVEMENTS, Action.A);
        MRow m = lock(scope, id);
        if (!"PENDING".equals(m.status())) {
            throw new Exceptions("error.common.alreadyDecided", HttpStatus.CONFLICT);
        }
        FinanceRulesService.Row rls = rules.get(scope.organizationId());
        if (!rls.selfApproval() && scope.personId().equals(m.submittedBy())) {
            throw new Exceptions("error.common.selfApproval", HttpStatus.FORBIDDEN);                                        // [V10]
        }
        if (scope.role() == RoleType.ORG_BRANCH_ADMIN && m.amount().compareTo(rls.approvalThresholdBranch()) > 0) {
            throw new Exceptions("error.finance.thresholdExceeded", HttpStatus.FORBIDDEN);                                  // umbral de sede
        }
        String receiptNo = null;
        if ("INCOME".equals(m.type()) && RECEIPT_CATEGORIES.contains(m.category()) && m.donorId() != null) {
            receiptNo = issueReceipt(scope.organizationId());
        }
        jdbc.update("update fin_movement set status = 'APPROVED', decided_by = :by, decided_at = :at, receipt_no = coalesce(:rn, receipt_no),"
                        + " version = version + 1 where id = :id",
                new MapSqlParameterSource("by", scope.personId()).addValue("at", Timestamp.from(clock.instant())).addValue("rn", receiptNo).addValue("id", id));
        notifications.toPersons(NotificationType.FINANCE_MOVEMENT_APPROVED, scope.organizationId(), List.of(m.submittedBy()),
                Map.of("amount", m.amount().toPlainString()), "/app/finance/movements/" + id, null);
        audit.record(new AuditService.Command(FinanceSupport.MOD_MOVEMENTS, "APPROVE", ENTITY, id, scope.organizationId(), m.branchId(),
                Map.of("receiptNo", receiptNo == null ? "" : receiptNo)));
        return get(scope, id);
    }

    @Transactional
    public FinanceDtos.MovementDetail reject(AuthenticatedActor actor, AccessScope scope, UUID id, String reasonText) {
        authz.require(actor, FinanceSupport.MOD_MOVEMENTS, Action.A);
        MRow m = lock(scope, id);
        if (!"PENDING".equals(m.status())) {
            throw new Exceptions("error.common.alreadyDecided", HttpStatus.CONFLICT);
        }
        FinanceRulesService.Row rls = rules.get(scope.organizationId());
        if (!rls.selfApproval() && scope.personId().equals(m.submittedBy())) {
            throw new Exceptions("error.common.selfApproval", HttpStatus.FORBIDDEN);                                        // [V10]
        }
        String reason = FinanceSupport.trim(reasonText, 500, "motivo");
        if (reason == null) {
            throw new Exceptions("error.common.reasonRequired", HttpStatus.BAD_REQUEST);
        }
        // No existe una columna dedicada a "motivo de rechazo": se reutiliza void_reason como "motivo de la decisión negativa"
        // (decisión propia, documentada aquí porque la migración V26 no agregó reject_reason).
        jdbc.update("update fin_movement set status = 'REJECTED', decided_by = :by, decided_at = :at, void_reason = :r, version = version + 1 where id = :id",
                new MapSqlParameterSource("by", scope.personId()).addValue("at", Timestamp.from(clock.instant())).addValue("r", reason).addValue("id", id));
        notifications.toPersons(NotificationType.FINANCE_MOVEMENT_REJECTED, scope.organizationId(), List.of(m.submittedBy()), Map.of("reason", reason),
                "/app/finance/movements/" + id, null);
        audit.record(new AuditService.Command(FinanceSupport.MOD_MOVEMENTS, "REJECT", ENTITY, id, scope.organizationId(), m.branchId(), Map.of("reason", reason)));
        return get(scope, id);
    }

    /**
     * [T10] Anular nunca borra. El original pasa a VOIDED (deja de contar en cualquier suma "status = APPROVED": fondos,
     * cuentas, presupuestos, caja). Se crea además un segundo movimiento ligado por {@code reversal_of}, con los mismos datos,
     * como rastro de auditoría visible en los listados — nace también VOIDED (no APPROVED) a propósito: como el original ya
     * deja de sumar en cuanto cambia de estado, agregar una fila APPROVED duplicaría o invertiría el saldo sin necesidad;
     * el saldo queda neutralizado por la sola transición del original, así "saldo = suma de movimientos APPROVED" no necesita
     * ningún caso especial para anulaciones.
     */
    @Transactional
    public FinanceDtos.MovementDetail voidMovement(AuthenticatedActor actor, AccessScope scope, UUID id, String reasonText) {
        authz.require(actor, FinanceSupport.MOD_MOVEMENTS, Action.Y);
        MRow m = lock(scope, id);
        if (!"APPROVED".equals(m.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, m.status());
        }
        periods.assertOpen(scope.organizationId(), m.movementDate());                                                       // [V11]
        String reason = FinanceSupport.trim(reasonText, 500, "motivo");
        if (reason == null) {
            throw new Exceptions("error.finance.voidReason", HttpStatus.BAD_REQUEST);
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("update fin_movement set status = 'VOIDED', void_reason = :r, voided_by = :by, voided_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("r", reason).addValue("by", scope.personId()).addValue("at", now).addValue("id", id));
        UUID reversalId = UUID.randomUUID();
        jdbc.update("insert into fin_movement (id, organization_id, branch_id, movement_date, type, category, fund_id, account_id, amount, currency, method,"
                        + " donor_id, anonymous, description, event_id, source_ref, cash_register_id, offering_count_id, status, submitted_by, decided_by,"
                        + " decided_at, void_reason, voided_by, voided_at, reversal_of, created_at, created_by)"
                        + " select :rid, organization_id, branch_id, movement_date, type, category, fund_id, account_id, amount, currency, method, donor_id,"
                        + " anonymous, concat('Reversión de anulación: ', coalesce(description, '')), event_id, source_ref, cash_register_id, offering_count_id,"
                        + " 'VOIDED', :by, :by, :at, :r, :by, :at, :id, :at, :by from fin_movement where id = :id",
                new MapSqlParameterSource("rid", reversalId).addValue("by", scope.personId()).addValue("at", now).addValue("r", reason).addValue("id", id));
        notifications.toPersons(NotificationType.FINANCE_MOVEMENT_VOIDED, scope.organizationId(), List.of(m.submittedBy()), Map.of("reason", reason),
                "/app/finance/movements/" + id, null);
        audit.record(new AuditService.Command(FinanceSupport.MOD_MOVEMENTS, "VOID", ENTITY, id, scope.organizationId(), m.branchId(),
                Map.of("reason", reason, "reversalId", reversalId.toString())));
        return get(scope, id);
    }

    // No existe DELETE: los movimientos se anulan (voidMovement), nunca se borran; a propósito no hay ningún endpoint
    // DELETE /movements/{id} [error.finance.cannotDelete queda solo como mensaje documentado en el spec, sin ruta que lo dispare].

    // ---------------------------------------------------------------- recibo [D3/V19]

    private String issueReceipt(UUID orgId) {
        int year = LocalDate.now(clock).getYear();
        Integer number = jdbc.queryForObject("insert into fin_receipt_counter (organization_id, year, last_number) values (:o, :y, 1)"
                        + " on conflict (organization_id, year) do update set last_number = fin_receipt_counter.last_number + 1 returning last_number",
                new MapSqlParameterSource("o", orgId).addValue("y", year), Integer.class);
        return "REC-" + year + "-" + String.format("%06d", number);
    }

    // ---------------------------------------------------------------- adjuntos [D6]

    private record AttachmentChecked(MultipartFile file, String safeName, String contentType) {
    }

    private List<AttachmentChecked> checkFiles(List<MultipartFile> files) {
        List<MultipartFile> real = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (real.size() > MAX_FILES) {
            throw new Exceptions("error.common.invalid", HttpStatus.UNPROCESSABLE_ENTITY, "adjuntos");
        }
        List<AttachmentChecked> out = new ArrayList<>();
        for (MultipartFile f : real) {
            if (f.getSize() > MAX_FILE_BYTES) {
                throw new Exceptions("error.common.fileSize", HttpStatus.UNPROCESSABLE_ENTITY, "10 MB");
            }
            String original = f.getOriginalFilename() == null ? "comprobante" : f.getOriginalFilename();
            String base = original.replace('\\', '/');
            base = base.substring(base.lastIndexOf('/') + 1).trim();
            String safe = base.replaceAll("[^A-Za-z0-9._ -]", "_");
            if (safe.isBlank()) {
                safe = "comprobante";
            }
            if (safe.length() > 150) {
                safe = safe.substring(safe.length() - 150);
            }
            String type = f.getContentType() == null ? "application/octet-stream" : f.getContentType();
            out.add(new AttachmentChecked(f, safe, type));
        }
        return out;
    }

    private void saveAttachments(AccessScope scope, UUID movementId, List<AttachmentChecked> files) {
        for (AttachmentChecked c : files) {
            try {
                String key = "org/" + scope.organizationId() + "/finance/" + movementId + "/" + UUID.randomUUID() + "-" + c.safeName();
                storage.put(key, c.file().getBytes(), c.contentType());
                jdbc.update("insert into fin_movement_attachment (id, movement_id, storage_key, filename, uploaded_at, uploaded_by)"
                                + " values (:id, :m, :k, :fn, :at, :by)",
                        new MapSqlParameterSource("id", UUID.randomUUID()).addValue("m", movementId).addValue("k", key).addValue("fn", c.safeName())
                                .addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()));
            } catch (IOException e) {
                throw new Exceptions("error.common.storage", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    @Transactional
    public FinanceDtos.MovementDetail addAttachments(AuthenticatedActor actor, AccessScope scope, UUID id, List<MultipartFile> files) {
        authz.require(actor, FinanceSupport.MOD_MOVEMENTS, Action.E);
        MRow m = lock(scope, id);
        if (!Set.of("PENDING", "APPROVED").contains(m.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, m.status());
        }
        List<AttachmentChecked> checked = checkFiles(files);
        if (checked.isEmpty()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "archivo");
        }
        saveAttachments(scope, id, checked);
        audit.record(new AuditService.Command(FinanceSupport.MOD_MOVEMENTS, "ATTACHMENT_ADD", ENTITY, id, scope.organizationId(), m.branchId(), Map.of("count", checked.size())));
        return get(scope, id);
    }

    @Transactional
    public FinanceDtos.MovementDetail deleteAttachment(AuthenticatedActor actor, AccessScope scope, UUID id, UUID attachmentId) {
        authz.require(actor, FinanceSupport.MOD_MOVEMENTS, Action.E);
        MRow m = lock(scope, id);
        List<String> keys = jdbc.query("select storage_key from fin_movement_attachment where id = :id and movement_id = :m",
                new MapSqlParameterSource("id", attachmentId).addValue("m", id), (rs, i) -> rs.getString(1));
        if (keys.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        jdbc.update("delete from fin_movement_attachment where id = :id", new MapSqlParameterSource("id", attachmentId));
        storage.delete(keys.get(0));
        audit.record(new AuditService.Command(FinanceSupport.MOD_MOVEMENTS, "ATTACHMENT_DELETE", ENTITY, id, scope.organizationId(), m.branchId(), Map.of()));
        return get(scope, id);
    }

    // ---------------------------------------------------------------- consolidado [T14]

    @Transactional(readOnly = true)
    public FinanceDtos.ConsolidatedResponse consolidated(AccessScope scope, FinanceDtos.ConsolidatedRequest req) {
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(FinanceSupport.visibleByBranch(scope, ps, "m")).append(" and m.status = 'APPROVED'");
        if (req != null && req.from() != null) {
            w.append(" and m.movement_date >= :df");
            ps.addValue("df", java.sql.Date.valueOf(req.from()));
        }
        if (req != null && req.to() != null) {
            w.append(" and m.movement_date <= :dt");
            ps.addValue("dt", java.sql.Date.valueOf(req.to()));
        }
        if (req != null && req.branchId() != null) {
            w.append(" and m.branch_id = :fb");
            ps.addValue("fb", req.branchId());
        }
        List<FinanceDtos.ConsolidatedRow> rows = jdbc.query("select m.branch_id, b.name as branch_name, m.fund_id, fu.name as fund_name, m.category, m.type,"
                        + " sum(m.amount) as total from fin_movement m join branch b on b.id = m.branch_id join fin_fund fu on fu.id = m.fund_id where " + w
                        + " group by m.branch_id, b.name, m.fund_id, fu.name, m.category, m.type order by b.name, fu.name, m.category",
                ps, (rs, i) -> new FinanceDtos.ConsolidatedRow((UUID) rs.getObject(1), rs.getString(2), (UUID) rs.getObject(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getBigDecimal(7).toPlainString()));
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (FinanceDtos.ConsolidatedRow row : rows) {
            if ("INCOME".equals(row.type())) {
                income = income.add(new BigDecimal(row.total()));
            } else {
                expense = expense.add(new BigDecimal(row.total()));
            }
        }
        return new FinanceDtos.ConsolidatedResponse(rows, income.toPlainString(), expense.toPlainString(), income.subtract(expense).toPlainString());
    }
}
