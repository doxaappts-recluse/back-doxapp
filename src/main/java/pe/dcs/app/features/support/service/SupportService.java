package pe.dcs.app.features.support.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.dcs.app.features.approval.service.ApprovalEngine;
import pe.dcs.app.features.audit.service.NameLookup;
import pe.dcs.app.features.support.domain.*;
import pe.dcs.app.features.support.dto.*;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.storage.FileStorageService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * M22 · casos de soporte. Dos vistas del mismo caso:
 * organización (N2/N3): ORG_ADMIN ve todos los de su organización, ORG_BRANCH_ADMIN los de sus sedes y los que abrió,
 * ORG_USER solo los que abrió; jamás recibe notas internas (V5). Plataforma (N1): ve, asigna, responde, resuelve y cierra.
 * Flujo: OPEN → (responde plataforma) WAITING_ORG ⇄ (responde la organización) WAITING_PLATFORM → RESOLVED → CLOSED.
 */
@Service
@RequiredArgsConstructor
public class SupportService {

    private static final String MODULE = "SUPPORT";
    private static final Pattern CODE = Pattern.compile("^(?:CS-?)?0*(\\d{1,9})$", Pattern.CASE_INSENSITIVE);
    private static final int SUBJECT_MIN = 5;
    private static final int SUBJECT_MAX = 150;
    private static final int BODY_MAX = 4000;
    private static final int MAX_PAGE_SIZE = 100;

    private final SupportCaseRepository cases;
    private final SupportMessageRepository messages;
    private final SupportAttachmentRepository attachments;
    private final SupportAttachmentPolicy policy;
    private final FileStorageService storage;
    private final AuditService audit;
    private final NameLookup names;
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final Clock clock;
    private final ApprovalEngine approvals;

    @Value("${support.auto-close-days:7}")
    private int autoCloseDays;

    // =====================================================================================================
    // Organización (N2/N3)
    // =====================================================================================================

    @Transactional
    public OrgCaseResponse open(AuthenticatedActor actor, String categoryRaw, String priorityRaw, String subjectRaw, String bodyRaw,
                                UUID branchId, List<MultipartFile> files, String requestedTypeRaw, NewBranchDraft branchDraft) {
        requireOrgRole(actor);
        String subject = subjectRaw == null ? "" : subjectRaw.trim();
        if (subject.length() < SUBJECT_MIN || subject.length() > SUBJECT_MAX) {
            throw new Exceptions("error.support.subjectLength", HttpStatus.BAD_REQUEST, SUBJECT_MIN, SUBJECT_MAX);
        }
        String body = body(bodyRaw);
        SupportCategory category = parse(SupportCategory.class, categoryRaw, "category");
        SupportPriority priority = priorityRaw == null || priorityRaw.isBlank() ? SupportPriority.NORMAL : parse(SupportPriority.class, priorityRaw, "priority");
        if (category.orgAdminOnly() && actor.role() != RoleType.ORG_ADMIN) {
            throw new Exceptions("error.support.categoryRestricted", HttpStatus.FORBIDDEN);
        }
        // M22-T11/T12 · CONTRACT_CHANGE y NEW_BRANCH abren, además del caso, una ApprovalRequest que se aprueba (y para
        // NEW_BRANCH_REQUEST se ejecuta) al resolver el caso — ver SupportCase.approvalRequestId y changeStatus() más abajo.
        String requestType = null;
        Map<String, Object> requestPayload = null;
        String requestSummary = null;
        if (category == SupportCategory.CONTRACT_CHANGE) {
            String t = requestedTypeRaw == null ? "" : requestedTypeRaw.trim().toUpperCase();
            if (!Set.of("UPGRADE", "DOWNGRADE", "RENEWAL").contains(t)) {
                throw new Exceptions("error.support.requestedTypeInvalid", HttpStatus.BAD_REQUEST);
            }
            requestType = "CONTRACT_REQUEST";
            requestPayload = Map.of("requestedType", t);
            requestSummary = "[Solicitud de cambio de contrato] tipo=" + t;
        } else if (category == SupportCategory.NEW_BRANCH) {
            if (branchDraft == null || blank(branchDraft.name()) || blank(branchDraft.code())) {
                throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "branchName/branchCode");
            }
            if (branchDraft.name().trim().length() > 100 || branchDraft.code().trim().length() > 10) {
                throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, "branchName/branchCode");
            }
            requestType = "NEW_BRANCH_REQUEST";
            requestPayload = branchPayload(branchDraft);
            requestSummary = "[Solicitud de nueva sede] nombre=" + branchDraft.name().trim() + " código=" + branchDraft.code().trim().toUpperCase();
        }
        UUID branch = resolveBranch(actor, branchId);
        List<SupportAttachmentPolicy.Checked> checked = policy.check(files);

        Instant now = clock.instant();
        String openerName = personName(actor.ownerId());
        SupportCase c = new SupportCase();
        c.setOrganizationId(actor.organizationId());
        c.setBranchId(branch);
        c.setOpenedBy(actor.ownerId());
        c.setOpenedByName(openerName);
        c.setCaseNumber(jdbc.queryForObject("select nextval('support_case_seq')", Long.class));
        c.setCategory(category);
        c.setPriority(priority);
        c.setSubject(subject);
        c.setStatus(SupportStatus.OPEN);
        c.setSlaDueAt(SupportRules.slaDue(now, priority));
        c.setLastActivityAt(now);
        c = cases.save(c);

        String fullBody = requestSummary == null ? body : requestSummary + "\n\n" + body;
        SupportMessage first = addMessage(c, "MESSAGE", "PERSON", actor.ownerId(), openerName, fullBody, false, now);
        store(c, first, checked, now);

        if (requestType != null) {
            // approval_request.branch_id es NOT NULL (pensado para "la sede que decide"): CONTRACT_CHANGE/NEW_BRANCH no
            // siempre traen sede (ORG_ADMIN puede abrir el caso sin una activa) y aquí no hay ninguna sede que decida
            // (decide SYSTEM_ADMIN de plataforma) — se usa la sede principal de la organización solo para satisfacer
            // la columna; decideInternal() nunca comprueba alcance de sede, así que no afecta quién puede aprobar.
            UUID reqBranch = c.getBranchId() != null ? c.getBranchId() : mainBranchId(c.getOrganizationId());
            UUID reqId = approvals.open(new ApprovalEngine.NewRequest(c.getOrganizationId(), reqBranch, null, requestType,
                    "SUPPORT_CASE", c.getId(), actor.ownerId(), subject, requestPayload));
            c.setApprovalRequestId(reqId);
            cases.save(c);
        }

        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("caseCode", SupportRules.code(c.getCaseNumber()));
        diff.put("category", category.name());
        diff.put("priority", priority.name());
        diff.put("subject", subject);
        diff.put("attachments", checked.size());
        audit.record(new AuditService.Command(MODULE, "CREATE", "SupportCase", c.getId(), c.getOrganizationId(), c.getBranchId(), diff));
        notifications.toStaff(NotificationType.SUPPORT_NEW_CASE, notifications.staff("SYSTEM_ADMIN", "SYSTEM_SUPPORT"),
                caseParams(c), "/platform/support/detail/" + c.getId(), null);
        return orgDetail(actor, c);
    }

    /** Payload de NEW_BRANCH_REQUEST: mismos nombres de campo que {@link NewBranchDraft} para que el handler los relea sin ambigüedad. */
    private static Map<String, Object> branchPayload(NewBranchDraft d) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", d.name().trim());
        p.put("code", d.code().trim().toUpperCase());
        putIfPresent(p, "displayName", d.displayName());
        putIfPresent(p, "phone", d.phone());
        putIfPresent(p, "email", d.email());
        putIfPresent(p, "openingDate", d.openingDate());
        putIfPresent(p, "timezone", d.timezone());
        putIfPresent(p, "addressLine", d.addressLine());
        putIfPresent(p, "addressCity", d.addressCity());
        putIfPresent(p, "addressCountry", d.addressCountry());
        return p;
    }

    private static void putIfPresent(Map<String, Object> p, String key, String value) {
        if (!blank(value)) {
            p.put(key, value.trim());
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** Sede principal de la organización (siempre existe y está activa, [V5] de M04) — ver comentario en {@code open()}. */
    private UUID mainBranchId(UUID orgId) {
        List<UUID> id = jdbc.queryForList("select id from branch where organization_id = ? and main = true limit 1", UUID.class, orgId);
        if (id.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return id.get(0);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrgCaseResponse> searchOrg(AuthenticatedActor actor, CaseSearchRequest req) {
        requireOrgRole(actor);
        CaseSearchRequest.Filters f = filters(req);
        Specification<SupportCase> spec = orgVisible(actor).and(common(f, false));
        if (f.branchId() != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("branchId"), f.branchId()));
        }
        if ("ME".equalsIgnoreCase(f.assignee())) {
            UUID me = actor.ownerId();
            spec = spec.and((r, q, cb) -> cb.equal(r.get("openedBy"), me));
        }
        Page<SupportCase> page = cases.findAll(spec, pageable(req, Sort.by(Sort.Direction.DESC, "lastActivityAt")));
        List<OrgCaseResponse> content = toOrg(actor, page.getContent(), false);
        return new PageResponse<>(content, new PaginationResponse((int) page.getTotalElements(), page.getTotalPages(), page.getSize(), page.getNumber()));
    }

    @Transactional(readOnly = true)
    public OrgSummary summaryOrg(AuthenticatedActor actor) {
        requireOrgRole(actor);
        Map<SupportStatus, Long> counts = new EnumMap<>(SupportStatus.class);
        Specification<SupportCase> vis = orgVisible(actor);
        for (SupportStatus s : SupportStatus.values()) {
            counts.put(s, cases.count(vis.and((r, q, cb) -> cb.equal(r.get("status"), s))));
        }
        return new OrgSummary(counts.get(SupportStatus.OPEN), counts.get(SupportStatus.WAITING_ORG), counts.get(SupportStatus.WAITING_PLATFORM),
                counts.get(SupportStatus.RESOLVED), counts.get(SupportStatus.CLOSED));
    }

    @Transactional(readOnly = true)
    public OrgCaseResponse detailOrg(AuthenticatedActor actor, UUID id) {
        return orgDetail(actor, visible(actor, id));
    }

    @Transactional
    public OrgCaseResponse replyOrg(AuthenticatedActor actor, UUID id, String bodyRaw, List<MultipartFile> files) {
        SupportCase c = visible(actor, id);
        requireNotClosed(c);
        String body = body(bodyRaw);
        List<SupportAttachmentPolicy.Checked> checked = policy.check(files);
        Instant now = clock.instant();

        SupportMessage m = addMessage(c, "MESSAGE", "PERSON", actor.ownerId(), personName(actor.ownerId()), body, false, now);
        store(c, m, checked, now);

        SupportStatus before = c.getStatus();
        if (before == SupportStatus.RESOLVED) {
            c.setResolvedAt(null);
            c.setStatus(SupportStatus.WAITING_PLATFORM);
            addMessage(c, "EVENT", "SYSTEM", null, "Sistema", "REOPENED", false, now);
        } else if (before == SupportStatus.WAITING_ORG) {
            c.setStatus(SupportStatus.WAITING_PLATFORM);
        }
        c.setLastActivityAt(now);
        cases.save(c);

        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("attachments", checked.size());
        diff.put("statusFrom", before.name());
        diff.put("statusTo", c.getStatus().name());
        audit.record(new AuditService.Command(MODULE, "REPLY", "SupportCase", c.getId(), c.getOrganizationId(), c.getBranchId(), diff));
        List<UUID> attend = c.getAssigneeId() != null ? List.of(c.getAssigneeId()) : notifications.staff("SYSTEM_ADMIN", "SYSTEM_SUPPORT");
        notifications.toStaff(NotificationType.SUPPORT_ORG_REPLY, attend, caseParams(c), "/platform/support/detail/" + c.getId(), null);
        return orgDetail(actor, c);
    }

    @Transactional
    public OrgCaseResponse rate(AuthenticatedActor actor, UUID id, RateRequest req) {
        SupportCase c = visible(actor, id);
        if (actor.role() != RoleType.ORG_ADMIN && !actor.ownerId().equals(c.getOpenedBy())) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        if (c.getStatus() != SupportStatus.RESOLVED && c.getStatus() != SupportStatus.CLOSED) {
            throw new Exceptions("error.support.notResolved", HttpStatus.CONFLICT);
        }
        if (c.getSatisfaction() != null) {
            throw new Exceptions("error.support.alreadyRated", HttpStatus.CONFLICT);
        }
        Integer rating = req == null ? null : req.rating();
        if (rating == null || rating < 1 || rating > 5) {
            throw new Exceptions("error.support.ratingInvalid", HttpStatus.BAD_REQUEST);
        }
        String comment = req.comment() == null || req.comment().isBlank() ? null : req.comment().trim();
        if (comment != null && comment.length() > 500) {
            throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, "comment", 500);
        }
        Instant now = clock.instant();
        c.setSatisfaction(rating.shortValue());
        c.setSatisfactionComment(comment);
        if (c.getStatus() == SupportStatus.RESOLVED) {
            c.setStatus(SupportStatus.CLOSED);
            c.setClosedAt(now);
            addMessage(c, "EVENT", "PERSON", actor.ownerId(), personName(actor.ownerId()), "CONFIRMED", false, now);
        }
        c.setLastActivityAt(now);
        cases.save(c);
        audit.record(new AuditService.Command(MODULE, "RATE", "SupportCase", c.getId(), c.getOrganizationId(), c.getBranchId(),
                Map.of("rating", rating)));
        return orgDetail(actor, c);
    }

    @Transactional(readOnly = true)
    public AttachmentContent attachmentOrg(AuthenticatedActor actor, UUID caseId, UUID attachmentId) {
        SupportCase c = visible(actor, caseId);
        return download(c, attachmentId, false);
    }

    // =====================================================================================================
    // Plataforma (N1)
    // =====================================================================================================

    @Transactional(readOnly = true)
    public PageResponse<StaffCaseResponse> searchStaff(AuthenticatedActor actor, CaseSearchRequest req) {
        requireStaff(actor);
        CaseSearchRequest.Filters f = filters(req);
        Instant now = clock.instant();
        Specification<SupportCase> spec = common(f, true);
        if (f.organizationId() != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("organizationId"), f.organizationId()));
        }
        if (f.assignee() != null && !f.assignee().isBlank()) {
            String a = f.assignee().trim();
            if ("ME".equalsIgnoreCase(a)) {
                UUID me = actor.ownerId();
                spec = spec.and((r, q, cb) -> cb.equal(r.get("assigneeId"), me));
            } else if ("UNASSIGNED".equalsIgnoreCase(a)) {
                spec = spec.and((r, q, cb) -> cb.isNull(r.get("assigneeId")));
            } else {
                UUID id = uuid(a);
                spec = spec.and((r, q, cb) -> cb.equal(r.get("assigneeId"), id));
            }
        }
        if (Boolean.TRUE.equals(f.breached())) {
            spec = spec.and((r, q, cb) -> cb.or(
                    cb.and(cb.isNull(r.get("firstResponseAt")), r.get("status").in(SupportStatus.OPEN, SupportStatus.WAITING_PLATFORM),
                            cb.lessThan(r.<Instant>get("slaDueAt"), now)),
                    cb.and(cb.isNotNull(r.get("firstResponseAt")), cb.greaterThan(r.<Instant>get("firstResponseAt"), r.<Instant>get("slaDueAt")))));
        }
        Sort sort = Boolean.TRUE.equals(f.activeOnly()) ? Sort.by(Sort.Direction.ASC, "slaDueAt") : Sort.by(Sort.Direction.DESC, "lastActivityAt");
        Page<SupportCase> page = cases.findAll(spec, pageable(req, sort));
        return new PageResponse<>(toStaff(page.getContent(), false),
                new PaginationResponse((int) page.getTotalElements(), page.getTotalPages(), page.getSize(), page.getNumber()));
    }

    @Transactional(readOnly = true)
    public StaffSummary summaryStaff(AuthenticatedActor actor) {
        requireStaff(actor);
        Instant now = clock.instant();
        Map<String, Long> by = new HashMap<>();
        jdbc.query("select status, count(*) from support_case group by status", rs -> {
            by.put(rs.getString(1), rs.getLong(2));
        });
        long unassigned = jdbc.queryForObject("select count(*) from support_case where assignee_id is null and status in ('OPEN','WAITING_ORG','WAITING_PLATFORM')", Long.class);
        long mine = jdbc.queryForObject("select count(*) from support_case where assignee_id = ? and status in ('OPEN','WAITING_ORG','WAITING_PLATFORM')", Long.class, actor.ownerId());
        long breached = jdbc.queryForObject("""
                select count(*) from support_case
                 where (first_response_at is null and status in ('OPEN','WAITING_PLATFORM') and sla_due_at < ?)
                    or (first_response_at is not null and first_response_at > sla_due_at and status in ('OPEN','WAITING_ORG','WAITING_PLATFORM'))""",
                Long.class, Timestamp.from(now));
        return new StaffSummary(by.getOrDefault("OPEN", 0L), by.getOrDefault("WAITING_ORG", 0L), by.getOrDefault("WAITING_PLATFORM", 0L),
                by.getOrDefault("RESOLVED", 0L), unassigned, mine, breached);
    }

    @Transactional(readOnly = true)
    public StaffCaseResponse detailStaff(AuthenticatedActor actor, UUID id) {
        requireStaff(actor);
        return toStaff(List.of(load(id)), true).get(0);
    }

    @Transactional(readOnly = true)
    public List<AssigneeOption> assignees(AuthenticatedActor actor) {
        requireStaff(actor);
        return jdbc.query("select id, first_name || ' ' || last_name, staff_role from platform_staff where status = 'ACTIVE' order by first_name, last_name",
                (rs, i) -> new AssigneeOption(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)));
    }

    @Transactional
    public StaffCaseResponse replyStaff(AuthenticatedActor actor, UUID id, String bodyRaw, boolean internal, List<MultipartFile> files) {
        requireStaff(actor);
        SupportCase c = load(id);
        requireNotClosed(c);
        String body = body(bodyRaw);
        List<SupportAttachmentPolicy.Checked> checked = policy.check(files);
        Instant now = clock.instant();
        String me = staffName(actor.ownerId());

        SupportMessage m = addMessage(c, "MESSAGE", "STAFF", actor.ownerId(), me, body, internal, now);
        store(c, m, checked, now);

        SupportStatus before = c.getStatus();
        if (!internal) {
            if (c.getFirstResponseAt() == null) {
                c.setFirstResponseAt(now);
            }
            if (c.getAssigneeId() == null) {
                c.setAssigneeId(actor.ownerId());
                addMessage(c, "EVENT", "STAFF", actor.ownerId(), me, "ASSIGNED|" + me, true, now);
            }
            if (before == SupportStatus.RESOLVED) {
                c.setResolvedAt(null);
                addMessage(c, "EVENT", "SYSTEM", null, "Sistema", "REOPENED", false, now);
            }
            c.setStatus(SupportStatus.WAITING_ORG);
            c.setLastActivityAt(now);
        }
        cases.save(c);

        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("internal", internal);
        diff.put("attachments", checked.size());
        // Una nota interna no debe dejar rastro visible para la organización (V5): su evento va sin organización.
        UUID org = internal ? null : c.getOrganizationId();
        UUID branch = internal ? null : c.getBranchId();
        if (internal) {
            diff.put("organizationId", c.getOrganizationId());
        }
        audit.record(new AuditService.Command(MODULE, internal ? "INTERNAL_NOTE" : "REPLY", "SupportCase", c.getId(), org, branch, diff));
        if (!internal) {
            notifyOpener(NotificationType.SUPPORT_STAFF_REPLY, c);
        }
        return toStaff(List.of(c), true).get(0);
    }

    @Transactional
    public StaffCaseResponse assign(AuthenticatedActor actor, UUID id, AssignRequest req) {
        requireStaff(actor);
        SupportCase c = load(id);
        requireNotClosed(c);
        UUID target = req == null ? null : req.assigneeId();
        if (target != null) {
            Integer ok = jdbc.queryForObject("select count(*) from platform_staff where id = ? and status = 'ACTIVE'", Integer.class, target);
            if (ok == null || ok == 0) {
                throw new Exceptions("error.support.assigneeInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        if (Objects.equals(target, c.getAssigneeId())) {
            return toStaff(List.of(c), true).get(0);
        }
        Instant now = clock.instant();
        UUID previous = c.getAssigneeId();
        c.setAssigneeId(target);
        addMessage(c, "EVENT", "STAFF", actor.ownerId(), staffName(actor.ownerId()),
                target == null ? "UNASSIGNED" : "ASSIGNED|" + staffName(target), true, now);
        cases.save(c);
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("from", previous);
        diff.put("to", target);
        audit.record(new AuditService.Command(MODULE, "ASSIGN", "SupportCase", c.getId(), c.getOrganizationId(), c.getBranchId(), diff));
        if (target != null && !target.equals(actor.ownerId())) {
            notifications.toStaff(NotificationType.SUPPORT_ASSIGNED, List.of(target), caseParams(c), "/platform/support/detail/" + c.getId(), null);
        }
        return toStaff(List.of(c), true).get(0);
    }

    @Transactional
    public StaffCaseResponse changeStatus(AuthenticatedActor actor, UUID id, CaseStatusRequest req) {
        requireStaff(actor);
        SupportCase c = load(id);
        requireNotClosed(c);
        SupportStatus to = parse(SupportStatus.class, req == null ? null : req.status(), "status");
        SupportStatus from = c.getStatus();
        boolean valid = switch (to) {
            case RESOLVED -> from.isOpenish();
            case CLOSED -> true;
            case OPEN -> from == SupportStatus.RESOLVED;
            default -> false;
        };
        if (!valid) {
            throw new Exceptions("error.support.invalidTransition", HttpStatus.CONFLICT, from.name(), to.name());
        }
        String note = req.note() == null || req.note().isBlank() ? null : req.note().trim();
        if (note != null && note.length() > 500) {
            throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, "note", 500);
        }
        // M22-T11/T12 · resolver un caso con solicitud enlazada la aprueba de una vez (decideInternal: quien decide es
        // SYSTEM_ADMIN de plataforma, sin el alcance de sede de una organización, así que no pasa por el decide() público).
        // Para NEW_BRANCH_REQUEST esto ejecuta de verdad la creación en M04 dentro de esta misma transacción: si no hay
        // cupo (error.branch.maxReached) o cualquier otra validación falla, la excepción se propaga, el caso NO queda
        // resuelto y el staff responde a mano por el hilo del caso (p. ej. sugiriendo un upgrade) — nada de eso se automatiza.
        // decided_by en approval_request tiene FK a person(id); actor.ownerId() aquí es un staff_id (requireStaff arriba),
        // no un person_id, así que se pasa null — mismo valor que usa ApprovalEngine para una expiración automática
        // ("decide el sistema"); quién de plataforma resolvió el caso ya queda en el propio hilo del caso y su auditoría.
        if (to == SupportStatus.RESOLVED && c.getApprovalRequestId() != null) {
            approvals.decideInternal(c.getApprovalRequestId(), true, null, note);
        }
        Instant now = clock.instant();
        c.setStatus(to);
        switch (to) {
            case RESOLVED -> c.setResolvedAt(now);
            case CLOSED -> c.setClosedAt(now);
            case OPEN -> c.setResolvedAt(null);
            default -> {
            }
        }
        c.setLastActivityAt(now);
        String code = to == SupportStatus.OPEN ? "REOPENED" : to.name();
        addMessage(c, "EVENT", "STAFF", actor.ownerId(), staffName(actor.ownerId()), note == null ? code : code + "|" + note, false, now);
        cases.save(c);
        audit.record(new AuditService.Command(MODULE, "STATUS_CHANGE", "SupportCase", c.getId(), c.getOrganizationId(), c.getBranchId(),
                Map.of("from", from.name(), "to", to.name())));
        if (to == SupportStatus.RESOLVED) {
            notifyOpener(NotificationType.SUPPORT_RESOLVED, c);
        } else if (to == SupportStatus.CLOSED) {
            notifyOpener(NotificationType.SUPPORT_CLOSED, c);
        }
        return toStaff(List.of(c), true).get(0);
    }

    @Transactional
    public StaffCaseResponse update(AuthenticatedActor actor, UUID id, CaseUpdateRequest req) {
        requireStaff(actor);
        SupportCase c = load(id);
        requireNotClosed(c);
        Map<String, Object> diff = new LinkedHashMap<>();
        if (req != null && req.priority() != null) {
            SupportPriority p = parse(SupportPriority.class, req.priority(), "priority");
            if (p != c.getPriority()) {
                diff.put("priorityFrom", c.getPriority().name());
                diff.put("priorityTo", p.name());
                c.setPriority(p);
                c.setSlaDueAt(SupportRules.slaDue(c.getCreatedAt(), p));
            }
        }
        if (req != null && req.category() != null) {
            SupportCategory cat = parse(SupportCategory.class, req.category(), "category");
            if (cat != c.getCategory()) {
                diff.put("categoryFrom", c.getCategory().name());
                diff.put("categoryTo", cat.name());
                c.setCategory(cat);
            }
        }
        if (!diff.isEmpty()) {
            cases.save(c);
            audit.record(new AuditService.Command(MODULE, "UPDATE", "SupportCase", c.getId(), c.getOrganizationId(), c.getBranchId(), diff));
        }
        return toStaff(List.of(c), true).get(0);
    }

    @Transactional(readOnly = true)
    public AttachmentContent attachmentStaff(AuthenticatedActor actor, UUID caseId, UUID attachmentId) {
        requireStaff(actor);
        return download(load(caseId), attachmentId, true);
    }

    // =====================================================================================================
    // Mantenimiento: alertas de SLA y cierre automático
    // =====================================================================================================

    /** SLA vencido sin alertar → una sola alerta a plataforma; resueltos/en espera de la organización sin actividad → cerrados. */
    @Transactional
    public MaintenanceResult maintenance() {
        Instant now = clock.instant();
        int alerted = 0;
        for (SupportCase c : cases.findAll((r, q, cb) -> cb.and(
                cb.isNull(r.get("firstResponseAt")), cb.isNull(r.get("slaAlertedAt")),
                r.get("status").in(SupportStatus.OPEN, SupportStatus.WAITING_PLATFORM),
                cb.lessThan(r.<Instant>get("slaDueAt"), now)))) {
            c.setSlaAlertedAt(now);
            cases.save(c);
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("priority", c.getPriority().name());
            diff.put("slaDueAt", c.getSlaDueAt().toString());
            diff.put("organizationId", c.getOrganizationId());
            audit.record(new AuditService.Command(MODULE, "SLA_BREACH", "SupportCase", c.getId(), null, null, diff));
            List<UUID> owners = c.getAssigneeId() != null ? List.of(c.getAssigneeId()) : notifications.staff("SYSTEM_ADMIN");
            notifications.toStaff(NotificationType.SUPPORT_SLA_BREACH, owners, caseParams(c), "/platform/support/detail/" + c.getId(), "sla:" + c.getId());
            alerted++;
        }
        Instant cutoff = now.minus(Duration.ofDays(autoCloseDays));
        int closed = 0;
        for (SupportCase c : cases.findAll((r, q, cb) -> cb.and(
                r.get("status").in(SupportStatus.WAITING_ORG, SupportStatus.RESOLVED),
                cb.lessThan(r.<Instant>get("lastActivityAt"), cutoff)))) {
            SupportStatus from = c.getStatus();
            c.setStatus(SupportStatus.CLOSED);
            c.setClosedAt(now);
            c.setLastActivityAt(now);
            addMessage(c, "EVENT", "SYSTEM", null, "Sistema", "AUTO_CLOSED", false, now);
            cases.save(c);
            audit.record(new AuditService.Command(MODULE, "AUTO_CLOSE", "SupportCase", c.getId(), c.getOrganizationId(), c.getBranchId(),
                    Map.of("from", from.name(), "days", autoCloseDays)));
            notifyOpener(NotificationType.SUPPORT_AUTO_CLOSED, c);
            closed++;
        }
        return new MaintenanceResult(alerted, closed);
    }

    // =====================================================================================================
    // Alcance y utilidades
    // =====================================================================================================

    /** Parámetros del aviso: código, asunto y nombre de la organización. */
    private Map<String, String> caseParams(SupportCase c) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("code", SupportRules.code(c.getCaseNumber()));
        p.put("subject", c.getSubject());
        p.put("organization", notifications.organizationName(c.getOrganizationId()));
        return p;
    }

    /** Avisa a quien abrió el caso (una persona de la organización) con enlace a su pantalla de soporte. */
    private void notifyOpener(NotificationType type, SupportCase c) {
        notifications.toPersons(type, c.getOrganizationId(), List.of(c.getOpenedBy()), caseParams(c), "/app/support/detail/" + c.getId(), null);
    }

    private void requireOrgRole(AuthenticatedActor actor) {
        if (actor.isStaff() || actor.organizationId() == null || actor.role() == RoleType.MEMBER) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
    }

    private void requireStaff(AuthenticatedActor actor) {
        if (!actor.isStaff()) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
    }

    /** Casos visibles para quien consulta desde una organización (V13). */
    private Specification<SupportCase> orgVisible(AuthenticatedActor actor) {
        UUID org = actor.organizationId();
        UUID me = actor.ownerId();
        Set<UUID> branches = actor.branchIds() == null ? Set.of() : actor.branchIds();
        return switch (actor.role()) {
            case ORG_ADMIN -> (r, q, cb) -> cb.equal(r.get("organizationId"), org);
            case ORG_BRANCH_ADMIN -> (r, q, cb) -> {
                Predicate mine = cb.equal(r.get("openedBy"), me);
                Predicate byBranch = branches.isEmpty() ? cb.disjunction() : r.get("branchId").in(branches);
                return cb.and(cb.equal(r.get("organizationId"), org), cb.or(mine, byBranch));
            };
            case ORG_USER -> (r, q, cb) -> cb.and(cb.equal(r.get("organizationId"), org), cb.equal(r.get("openedBy"), me));
            default -> throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        };
    }

    private SupportCase visible(AuthenticatedActor actor, UUID id) {
        requireOrgRole(actor);
        return cases.findOne(orgVisible(actor).and((r, q, cb) -> cb.equal(r.get("id"), id)))
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private SupportCase load(UUID id) {
        return cases.findById(id).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private void requireNotClosed(SupportCase c) {
        if (c.getStatus() == SupportStatus.CLOSED) {
            throw new Exceptions("error.support.caseClosed", HttpStatus.CONFLICT);
        }
    }

    /** Sede del caso: la indicada (debe ser de la organización y, salvo ORG_ADMIN, de las del usuario) o la sede de trabajo activa. */
    private UUID resolveBranch(AuthenticatedActor actor, UUID requested) {
        Set<UUID> mine = actor.branchIds() == null ? Set.of() : actor.branchIds();
        if (requested != null) {
            Integer own = jdbc.queryForObject("select count(*) from branch where id = ? and organization_id = ?", Integer.class, requested, actor.organizationId());
            boolean allowed = actor.role() == RoleType.ORG_ADMIN || mine.contains(requested);
            if (own == null || own == 0 || !allowed) {
                throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
            }
            return requested;
        }
        UUID active = actor.activeBranchId();
        if (active != null && (actor.role() == RoleType.ORG_ADMIN || mine.contains(active))) {
            Integer own = jdbc.queryForObject("select count(*) from branch where id = ? and organization_id = ?", Integer.class, active, actor.organizationId());
            return own != null && own > 0 ? active : null;
        }
        return actor.role() != RoleType.ORG_ADMIN && mine.size() == 1 ? mine.iterator().next() : null;
    }

    private Specification<SupportCase> common(CaseSearchRequest.Filters f, boolean staff) {
        return (r, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.status() != null && !f.status().isEmpty()) {
                List<SupportStatus> st = f.status().stream().map(s -> parse(SupportStatus.class, s, "status")).toList();
                ps.add(r.get("status").in(st));
            }
            if (Boolean.TRUE.equals(f.activeOnly())) {
                ps.add(r.get("status").in(SupportStatus.OPEN, SupportStatus.WAITING_ORG, SupportStatus.WAITING_PLATFORM));
            }
            if (f.priority() != null && !f.priority().isBlank()) {
                ps.add(cb.equal(r.get("priority"), parse(SupportPriority.class, f.priority(), "priority")));
            }
            if (f.category() != null && !f.category().isBlank()) {
                ps.add(cb.equal(r.get("category"), parse(SupportCategory.class, f.category(), "category")));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String term = f.q().trim();
                String like = "%" + term.toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
                List<Predicate> any = new ArrayList<>();
                any.add(cb.like(cb.lower(r.get("subject")), like));
                any.add(cb.like(cb.lower(r.get("openedByName")), like));
                Matcher m = CODE.matcher(term);
                if (m.matches()) {
                    any.add(cb.equal(r.get("caseNumber"), Long.parseLong(m.group(1))));
                }
                ps.add(cb.or(any.toArray(new Predicate[0])));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    private static CaseSearchRequest.Filters filters(CaseSearchRequest req) {
        return req == null || req.filters() == null
                ? new CaseSearchRequest.Filters(null, null, null, null, null, null, null, null, null) : req.filters();
    }

    private static org.springframework.data.domain.Pageable pageable(CaseSearchRequest req, Sort sort) {
        PaginationRequest p = req == null || req.pagination() == null ? new PaginationRequest() : req.pagination();
        return PageRequest.of(Math.max(p.getPage(), 0), Math.min(Math.max(p.getSize(), 1), MAX_PAGE_SIZE), sort.and(Sort.by(Sort.Direction.DESC, "caseNumber")));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw, String field) {
        try {
            return Enum.valueOf(type, raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, field);
        }
    }

    private static UUID uuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "assignee");
        }
    }

    private static String body(String raw) {
        String b = raw == null ? "" : raw.trim();
        if (b.isEmpty() || b.length() > BODY_MAX) {
            throw new Exceptions("error.support.bodyLength", HttpStatus.BAD_REQUEST, BODY_MAX);
        }
        return b;
    }

    private String personName(UUID id) {
        return names.people(List.of(id)).getOrDefault(id, "—");
    }

    private String staffName(UUID id) {
        return names.staff(List.of(id)).getOrDefault(id, "Soporte");
    }

    private SupportMessage addMessage(SupportCase c, String kind, String authorType, UUID authorId, String authorName, String body,
                                      boolean internal, Instant now) {
        SupportMessage m = new SupportMessage();
        m.setCaseId(c.getId());
        m.setKind(kind);
        m.setAuthorType(authorType);
        m.setAuthorId(authorId);
        m.setAuthorName(authorName);
        m.setBody(body);
        m.setInternal(internal);
        m.setCreatedAt(now);
        return messages.save(m);
    }

    private void store(SupportCase c, SupportMessage m, List<SupportAttachmentPolicy.Checked> checked, Instant now) {
        for (SupportAttachmentPolicy.Checked f : checked) {
            SupportAttachment a = new SupportAttachment();
            a.setMessageId(m.getId());
            a.setCaseId(c.getId());
            a.setFileName(f.safeName());
            a.setContentType(f.contentType());
            a.setSizeBytes(f.file().getSize());
            a.setCreatedAt(now);
            a.setStorageKey("pending");
            a = attachments.save(a);
            String key = "org/" + c.getOrganizationId() + "/support/" + c.getId() + "/" + a.getId() + "-" + f.safeName();
            try {
                storage.put(key, f.file().getBytes(), f.contentType());
            } catch (java.io.IOException e) {
                throw new Exceptions("error.support.attachmentInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            a.setStorageKey(key);
            attachments.save(a);
        }
    }

    private AttachmentContent download(SupportCase c, UUID attachmentId, boolean staff) {
        SupportAttachment a = attachments.findByIdAndCaseId(attachmentId, c.getId())
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        if (!staff) {
            SupportMessage m = messages.findById(a.getMessageId()).orElse(null);
            if (m == null || m.isInternal()) {
                throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
            }
        }
        FileStorageService.StoredFile f = storage.get(a.getStorageKey())
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        return new AttachmentContent(a.getFileName(), a.getContentType(), f.data());
    }

    // ------------------------------------------------------------------ armado de respuestas

    private Map<UUID, List<AttachmentInfo>> attachmentsByMessage(Collection<SupportMessage> msgs) {
        if (msgs.isEmpty()) {
            return Map.of();
        }
        return attachments.findByMessageIdIn(msgs.stream().map(SupportMessage::getId).toList()).stream()
                .collect(Collectors.groupingBy(SupportAttachment::getMessageId,
                        Collectors.mapping(a -> new AttachmentInfo(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes()), Collectors.toList())));
    }

    private OrgCaseResponse orgDetail(AuthenticatedActor actor, SupportCase c) {
        return toOrg(actor, List.of(c), true).get(0);
    }

    private List<OrgCaseResponse> toOrg(AuthenticatedActor actor, List<SupportCase> list, boolean withMessages) {
        Map<UUID, String> branches = names.branches(list.stream().map(SupportCase::getBranchId).collect(Collectors.toSet()));
        Map<UUID, String> staff = names.staff(list.stream().map(SupportCase::getAssigneeId).collect(Collectors.toSet()));
        List<OrgCaseResponse> out = new ArrayList<>();
        for (SupportCase c : list) {
            List<OrgMessage> msgs = null;
            if (withMessages) {
                // Las notas internas nunca se consultan para la organización (V5): el repositorio ya las excluye.
                List<SupportMessage> raw = messages.findByCaseIdAndInternalFalseOrderByCreatedAtAsc(c.getId());
                Map<UUID, List<AttachmentInfo>> att = attachmentsByMessage(raw);
                msgs = raw.stream().map(m -> new OrgMessage(m.getId(), m.getKind(), m.getAuthorType(), m.getAuthorName(), m.getBody(),
                        m.getCreatedAt(), att.getOrDefault(m.getId(), List.of()))).toList();
            }
            boolean rateable = (c.getStatus() == SupportStatus.RESOLVED || c.getStatus() == SupportStatus.CLOSED) && c.getSatisfaction() == null
                    && (actor.role() == RoleType.ORG_ADMIN || actor.ownerId().equals(c.getOpenedBy()));
            out.add(new OrgCaseResponse(c.getId(), c.getCaseNumber(), SupportRules.code(c.getCaseNumber()), c.getBranchId(), branches.get(c.getBranchId()),
                    c.getOpenedBy(), c.getOpenedByName(), staff.get(c.getAssigneeId()), c.getCategory().name(), c.getPriority().name(), c.getSubject(),
                    c.getStatus().name(), c.getCreatedAt(), c.getLastActivityAt(), c.getResolvedAt(), c.getClosedAt(),
                    c.getSatisfaction() == null ? null : c.getSatisfaction().intValue(), c.getSatisfactionComment(),
                    c.getStatus() != SupportStatus.CLOSED, rateable, c.getVersion(), msgs));
        }
        return out;
    }

    private List<StaffCaseResponse> toStaff(List<SupportCase> list, boolean withMessages) {
        Instant now = clock.instant();
        Map<UUID, String> orgs = names.organizations(list.stream().map(SupportCase::getOrganizationId).collect(Collectors.toSet()));
        Map<UUID, String> branches = names.branches(list.stream().map(SupportCase::getBranchId).collect(Collectors.toSet()));
        Map<UUID, String> staff = names.staff(list.stream().map(SupportCase::getAssigneeId).collect(Collectors.toSet()));
        List<StaffCaseResponse> out = new ArrayList<>();
        for (SupportCase c : list) {
            List<StaffMessage> msgs = null;
            if (withMessages) {
                List<SupportMessage> raw = messages.findByCaseIdOrderByCreatedAtAsc(c.getId());
                Map<UUID, List<AttachmentInfo>> att = attachmentsByMessage(raw);
                msgs = raw.stream().map(m -> new StaffMessage(m.getId(), m.getKind(), m.getAuthorType(), m.getAuthorName(), m.getBody(),
                        m.isInternal(), m.getCreatedAt(), att.getOrDefault(m.getId(), List.of()))).toList();
            }
            out.add(new StaffCaseResponse(c.getId(), c.getCaseNumber(), SupportRules.code(c.getCaseNumber()), c.getOrganizationId(),
                    orgs.get(c.getOrganizationId()), c.getBranchId(), branches.get(c.getBranchId()), c.getOpenedBy(), c.getOpenedByName(),
                    c.getAssigneeId(), staff.get(c.getAssigneeId()), c.getCategory().name(), c.getPriority().name(), c.getSubject(),
                    c.getStatus().name(), c.getSlaDueAt(), c.getFirstResponseAt(), SupportRules.breached(c, now),
                    c.getCreatedAt(), c.getLastActivityAt(), c.getResolvedAt(), c.getClosedAt(),
                    c.getSatisfaction() == null ? null : c.getSatisfaction().intValue(), c.getSatisfactionComment(), c.getVersion(), msgs));
        }
        return out;
    }

}
