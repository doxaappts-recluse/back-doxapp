package pe.dcs.app.features.support.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.audit.service.NameLookup;
import pe.dcs.app.features.organization.domain.Organization;
import pe.dcs.app.features.organization.domain.OrganizationRepository;
import pe.dcs.app.features.support.domain.AssistedAccessGrant;
import pe.dcs.app.features.support.domain.AssistedAccessGrantRepository;
import pe.dcs.app.features.support.domain.SupportCase;
import pe.dcs.app.features.support.domain.SupportCaseRepository;
import pe.dcs.app.features.support.domain.SupportMessage;
import pe.dcs.app.features.support.domain.SupportMessageRepository;
import pe.dcs.app.features.support.dto.AssistedAccessDtos.ApproveRequest;
import pe.dcs.app.features.support.dto.AssistedAccessDtos.DenyRequest;
import pe.dcs.app.features.support.dto.AssistedAccessDtos.EnterResponse;
import pe.dcs.app.features.support.dto.AssistedAccessDtos.GrantResponse;
import pe.dcs.app.features.support.dto.AssistedAccessDtos.RequestAccess;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.ContractState;
import pe.dcs.app.security.TokenType;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.security.jwt.JwtService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * M22 (D3) · ciclo de vida del acceso asistido: nace de un caso de soporte abierto, el ORG_ADMIN de esa organización
 * aprueba/rechaza/revoca, y expira solo. Cada paso queda como evento en el propio hilo del caso (misma pantalla que
 * ya usan staff y organización, sin bandeja nueva) y auditado (SUPPORT). Ver {@code AuthorizationService} para cómo
 * el {@code scope} reemplaza el chequeo normal de nivel mientras el grant sigue ACTIVE, y {@code JwtService.issueUntil}
 * para el token que emite {@link #enter}.
 */
@Service
@RequiredArgsConstructor
public class AssistedAccessService {

    private static final String MODULE = "SUPPORT";
    private static final int MAX_MINUTES = 240;
    private static final int MAX_GRANTS_PER_DAY = 3;

    private final AssistedAccessGrantRepository grants;
    private final SupportCaseRepository cases;
    private final SupportMessageRepository messages;
    private final OrganizationRepository organizations;
    private final NameLookup names;
    private final AuditService audit;
    private final JwtService jwt;
    private final Clock clock;

    @Transactional
    public GrantResponse request(AuthenticatedActor actor, UUID caseId, RequestAccess req) {
        SupportCase c = cases.findById(caseId).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        if (!c.getStatus().isOpenish()) {
            throw new Exceptions("error.assisted.noCase", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        List<String> scope = req.scope() == null ? List.of() : req.scope();
        if (scope.isEmpty() || !AuthorizationService.ASSISTED_SCOPE_MODULES.containsAll(scope)) {
            throw new Exceptions("error.assisted.scopeNotAllowed", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String reason = req.reason() == null ? "" : req.reason().trim();
        if (reason.isBlank()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST);
        }
        UUID staffId = actor.ownerId();
        UUID orgId = c.getOrganizationId();
        if (grants.findByStaffIdAndOrganizationIdAndStatus(staffId, orgId, "ACTIVE").isPresent()
                || grants.findByStaffIdAndOrganizationIdAndStatus(staffId, orgId, "PENDING").isPresent()) {
            throw new Exceptions("error.assisted.alreadyActive", HttpStatus.CONFLICT);
        }
        Instant since = clock.instant().minusSeconds(24L * 3600);
        long today = grants.findByOrganizationIdAndRequestedAtAfter(orgId, since).size();
        if (today >= MAX_GRANTS_PER_DAY) {
            throw new Exceptions("error.assisted.limitPerDay", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        AssistedAccessGrant g = new AssistedAccessGrant();
        g.setOrganizationId(orgId);
        g.setCaseId(caseId);
        g.setStaffId(staffId);
        g.setScope(scope.toArray(new String[0]));
        g.setReason(reason);
        g.setRequestedAt(clock.instant());
        g.setStatus("PENDING");
        g = grants.save(g);

        String staffName = names.staff(Set.of(staffId)).getOrDefault(staffId, "Soporte de plataforma");
        Integer minutes = req.durationMinutes();
        String durationNote = minutes == null ? "" : (" Duración estimada: " + minutes + " min.");
        postEvent(c, "STAFF", staffId, staffName,
                staffName + " solicitó acceso asistido a: " + String.join(", ", scope) + ". Motivo: " + reason + "." + durationNote);
        audit.record(AuditService.Command.of(MODULE, "ASSISTED_REQUESTED", "AssistedAccessGrant", g.getId(), null));
        return toResponse(g, c);
    }

    @Transactional(readOnly = true)
    public List<GrantResponse> listForCase(UUID caseId) {
        SupportCase c = cases.findById(caseId).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        return grants.findByCaseIdOrderByRequestedAtDesc(caseId).stream().map(g -> toResponse(g, c)).toList();
    }

    @Transactional
    public GrantResponse approve(AuthenticatedActor actor, UUID grantId, ApproveRequest req) {
        AssistedAccessGrant g = ownedByOrg(actor, grantId);
        if (!"PENDING".equals(g.getStatus())) {
            throw new Exceptions("error.assisted.notPending", HttpStatus.CONFLICT);
        }
        int minutes = req.durationMinutes() == null ? MAX_MINUTES : req.durationMinutes();
        if (minutes < 1 || minutes > MAX_MINUTES) {
            throw new Exceptions("error.assisted.duration", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Instant now = clock.instant();
        g.setApprovedBy(actor.ownerId());
        g.setStartsAt(now);
        g.setExpiresAt(now.plusSeconds(minutes * 60L));
        g.setStatus("ACTIVE");
        try {
            g = grants.saveAndFlush(g);
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.assisted.alreadyActive", HttpStatus.CONFLICT);
        }
        SupportCase c = cases.findById(g.getCaseId()).orElseThrow();
        postEvent(c, "PERSON", actor.ownerId(), orgAdminName(actor), "Acceso asistido aprobado hasta " + g.getExpiresAt() + ".");
        audit.record(AuditService.Command.of(MODULE, "ASSISTED_APPROVED", "AssistedAccessGrant", g.getId(), null));
        return toResponse(g, c);
    }

    @Transactional
    public GrantResponse deny(AuthenticatedActor actor, UUID grantId, DenyRequest req) {
        AssistedAccessGrant g = ownedByOrg(actor, grantId);
        if (!"PENDING".equals(g.getStatus())) {
            throw new Exceptions("error.assisted.notPending", HttpStatus.CONFLICT);
        }
        g.setDeniedBy(actor.ownerId());
        g.setDeniedReason(req.reason());
        g.setStatus("DENIED");
        grants.save(g);
        SupportCase c = cases.findById(g.getCaseId()).orElseThrow();
        postEvent(c, "PERSON", actor.ownerId(), orgAdminName(actor), "Acceso asistido rechazado.");
        audit.record(AuditService.Command.of(MODULE, "ASSISTED_DENIED", "AssistedAccessGrant", g.getId(), null));
        return toResponse(g, c);
    }

    @Transactional
    public GrantResponse revokeByOrg(AuthenticatedActor actor, UUID grantId) {
        AssistedAccessGrant g = ownedByOrg(actor, grantId);
        if (!"ACTIVE".equals(g.getStatus())) {
            throw new Exceptions("error.assisted.notPending", HttpStatus.CONFLICT);
        }
        g.setRevokedAt(clock.instant());
        g.setRevokedBy(actor.ownerId());
        g.setStatus("REVOKED");
        grants.save(g);
        SupportCase c = cases.findById(g.getCaseId()).orElseThrow();
        postEvent(c, "PERSON", actor.ownerId(), orgAdminName(actor), "La organización revocó el acceso asistido.");
        audit.record(AuditService.Command.of(MODULE, "ASSISTED_REVOKED", "AssistedAccessGrant", g.getId(), null));
        return toResponse(g, c);
    }

    @Transactional
    public GrantResponse endByStaff(AuthenticatedActor actor, UUID grantId) {
        AssistedAccessGrant g = grants.findById(grantId).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        if (!actor.isStaff() || !g.getStaffId().equals(actor.ownerId())) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        if (!"ACTIVE".equals(g.getStatus())) {
            throw new Exceptions("error.assisted.notPending", HttpStatus.CONFLICT);
        }
        g.setRevokedAt(clock.instant());
        g.setRevokedBy(actor.ownerId());
        g.setStatus("REVOKED");
        grants.save(g);
        SupportCase c = cases.findById(g.getCaseId()).orElseThrow();
        String staffName = names.staff(Set.of(actor.ownerId())).getOrDefault(actor.ownerId(), "Soporte de plataforma");
        postEvent(c, "STAFF", actor.ownerId(), staffName, staffName + " terminó su acceso asistido.");
        audit.record(AuditService.Command.of(MODULE, "ASSISTED_ENDED", "AssistedAccessGrant", g.getId(), null));
        return toResponse(g, c);
    }

    /** Entra en modo asistido: token propio, vigente hasta que expire el grant (nunca los 15 min fijos de un ACCESS normal). */
    @Transactional
    public EnterResponse enter(AuthenticatedActor actor, UUID grantId) {
        AssistedAccessGrant g = grants.findById(grantId).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        if (!actor.isStaff() || !g.getStaffId().equals(actor.ownerId())) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        if (!g.isLiveActive(clock.instant())) {
            throw new Exceptions("error.assisted.expired", HttpStatus.FORBIDDEN);
        }
        AuthenticatedActor assisted = new AuthenticatedActor(actor.credentialId(), actor.actorType(), actor.ownerId(), actor.ownerId(),
                g.getOrganizationId(), Set.of(), null, actor.role(), ContractState.NONE, TokenType.ACCESS, g.getId());
        JwtService.IssuedToken token = jwt.issueUntil(assisted, g.getExpiresAt());
        audit.record(AuditService.Command.of(MODULE, "ASSISTED_ENTERED", "AssistedAccessGrant", g.getId(), null));
        Organization org = organizations.findById(g.getOrganizationId()).orElse(null);
        return new EnterResponse(token.token(), token.expiresAt(), g.getOrganizationId(), org == null ? null : org.getName(), List.of(g.getScope()));
    }

    private AssistedAccessGrant ownedByOrg(AuthenticatedActor actor, UUID grantId) {
        AssistedAccessGrant g = grants.findById(grantId).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        if (!g.getOrganizationId().equals(actor.organizationId())) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return g;
    }

    private void postEvent(SupportCase c, String authorType, UUID authorId, String authorName, String body) {
        SupportMessage m = new SupportMessage();
        m.setCaseId(c.getId());
        m.setKind("EVENT");
        m.setAuthorType(authorType);
        m.setAuthorId(authorId);
        m.setAuthorName(authorName);
        m.setBody(body);
        m.setInternal(false);
        m.setCreatedAt(clock.instant());
        messages.save(m);
        c.setLastActivityAt(clock.instant());
        cases.save(c);
    }

    /**
     * Housekeeping (no es control de seguridad: la revocación real ya la hace {@code AuthorizationService} leyendo
     * el grant en vivo en cada permiso) — solo pone al día el estado visible de los grants ACTIVE cuyo expiresAt ya
     * pasó, para que listados/reportes no los muestren como activos para siempre.
     */
    @Transactional
    public int expireOverdue() {
        List<AssistedAccessGrant> overdue = grants.findByStatusAndExpiresAtBefore("ACTIVE", clock.instant());
        for (AssistedAccessGrant g : overdue) {
            g.setStatus("EXPIRED");
            grants.save(g);
        }
        return overdue.size();
    }

    private String orgAdminName(AuthenticatedActor actor) {
        return names.people(Set.of(actor.ownerId())).getOrDefault(actor.ownerId(), "Administrador");
    }

    private GrantResponse toResponse(AssistedAccessGrant g, SupportCase c) {
        String staffName = names.staff(Set.of(g.getStaffId())).getOrDefault(g.getStaffId(), null);
        String orgName = names.organizations(Set.of(g.getOrganizationId())).getOrDefault(g.getOrganizationId(), null);
        String approvedByName = g.getApprovedBy() == null ? null : names.people(Set.of(g.getApprovedBy())).get(g.getApprovedBy());
        return new GrantResponse(g.getId(), g.getOrganizationId(), orgName, c.getId(), c.getCaseNumber(), g.getStaffId(), staffName,
                List.of(g.getScope()), g.getReason(), g.getRequestedAt(), g.getApprovedBy(), approvedByName, g.getDeniedReason(),
                g.getStartsAt(), g.getExpiresAt(), g.getRevokedAt(), g.getStatus());
    }
}
