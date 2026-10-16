package pe.dcs.app.features.access.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.access.domain.AccessStatus;
import pe.dcs.app.features.access.domain.ActorType;
import pe.dcs.app.features.access.domain.Credential;
import pe.dcs.app.features.access.domain.PlatformStaff;
import pe.dcs.app.features.access.domain.StaffRole;
import pe.dcs.app.features.access.dto.StaffCreateRequest;
import pe.dcs.app.features.access.dto.StaffResponse;
import pe.dcs.app.features.access.dto.StaffRoleRequest;
import pe.dcs.app.features.access.dto.StaffSearchRequest;
import pe.dcs.app.features.access.dto.StaffStatusRequest;
import pe.dcs.app.features.access.dto.StaffUpdateRequest;
import pe.dcs.app.features.access.repo.CredentialRepository;
import pe.dcs.app.features.access.repo.PlatformStaffRepository;
import pe.dcs.app.features.auth.service.InvitationService;
import pe.dcs.app.features.auth.service.RefreshTokenService;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessStateCache;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.vo.ContactValidator;
import pe.dcs.app.shared.vo.DocumentType;
import pe.dcs.app.shared.vo.DocumentValidator;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.util.pagination.SortRequest;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * M05 · Personal de plataforma (N1). Alta con invitación (72 h), edición, cambio de rol y de estado.
 * Reglas: [V1] documento y correo únicos · [V2] no dejar la plataforma sin SYSTEM_ADMIN activo ·
 * [V3] nadie cambia su propio rol ni estado · [V5] SYSTEM_SUPPORT no gestiona (lo aplica @ModuleAccess: solo V).
 * Inactivar o cambiar el rol cierra las sesiones de la persona (refresh tokens) y su acceso deja de valer en segundos
 * (ver {@link AccessStateCache}).
 */
@Service
@RequiredArgsConstructor
public class PlatformStaffService {

    static final String MODULE = "PLATFORM_STAFF";
    static final String ENTITY = "PlatformStaff";

    private final PlatformStaffRepository staffRepository;
    private final CredentialRepository credentials;
    private final InvitationService invitations;
    private final RefreshTokenService refreshTokens;
    private final AccessStateCache accessState;
    private final AuditService audit;
    private final Clock clock;

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<StaffResponse> search(StaffSearchRequest req, AuthenticatedActor actor) {
        StaffSearchRequest.Filters f = req == null || req.filters() == null
                ? new StaffSearchRequest.Filters(null, null, null) : req.filters();

        Specification<PlatformStaff> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            if (hasText(f.q())) {
                String like = "%" + f.q().trim().toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), like),
                        cb.like(cb.lower(root.get("lastName")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("docNumber")), like)));
            }
            if (hasText(f.staffRole())) {
                ps.add(cb.equal(root.get("staffRole"), parseEnum(StaffRole.class, f.staffRole())));
            }
            if (hasText(f.status())) {
                ps.add(cb.equal(root.get("status"), parseEnum(AccessStatus.class, f.status())));
            }
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        List<SortRequest> sorts = req == null ? null : req.sorts();
        if (sorts == null || sorts.isEmpty()) {
            SortRequest byCreated = new SortRequest();
            byCreated.setKey("createdAt");
            byCreated.setDirection("DESC");
            sorts = List.of(byCreated);
        }
        Pageable pageable = PageableUtil.buildPageable(req == null ? null : req.pagination(), sorts, PlatformStaffService::sortField);
        Page<PlatformStaff> page = staffRepository.findAll(spec, pageable);

        Map<UUID, Credential> creds = credentials.findByStaffIdIn(page.getContent().stream().map(PlatformStaff::getId).toList())
                .stream().collect(Collectors.toMap(Credential::getStaffId, Function.identity()));
        List<StaffResponse> content = page.getContent().stream()
                .map(s -> toResponse(s, creds.get(s.getId()), actor)).toList();
        return new PageResponse<>(content, new PaginationResponse(
                (int) page.getTotalElements(), page.getTotalPages(), page.getSize(), page.getNumber()));
    }

    @Transactional(readOnly = true)
    public StaffResponse get(UUID id, AuthenticatedActor actor) {
        PlatformStaff s = find(id);
        return toResponse(s, credentials.findByStaffId(id).orElse(null), actor);
    }

    // ---------------------------------------------------------------- alta / edición

    @Transactional
    public StaffResponse create(StaffCreateRequest req, AuthenticatedActor actor) {
        String email = ContactValidator.normalizeEmail(req.email());
        String doc = DocumentValidator.normalize(req.docNumber());
        String phone = blankToNull(req.phone());
        validateIdentity(req.docType(), doc, email, phone, null);

        PlatformStaff s = new PlatformStaff();
        s.setFirstName(req.firstName().trim());
        s.setLastName(req.lastName().trim());
        s.setDocType(req.docType());
        s.setDocNumber(doc);
        s.setEmail(email);
        s.setPhone(phone);
        s.setPosition(blankToNull(req.position()));
        s.setStaffRole(req.staffRole());
        s.setHireDate(req.hireDate());
        s.setStatus(AccessStatus.INVITED);
        s = staffRepository.saveAndFlush(s);

        Credential c = new Credential();
        c.setActorType(ActorType.STAFF);
        c.setStaffId(s.getId());
        c.setUsername(email);
        c.setStatus(AccessStatus.INVITED);
        c.setMustChangePassword(false);
        c = credentials.saveAndFlush(c);

        invitations.sendInvite(c.getId(), email, s.fullName(), null, locale());
        audit.record(AuditService.Command.of(MODULE, "CREATE", ENTITY, s.getId(),
                diff("staffRole", s.getStaffRole(), "email", email, "docType", s.getDocType())));
        return toResponse(s, c, actor);
    }

    @Transactional
    public StaffResponse update(UUID id, StaffUpdateRequest req, AuthenticatedActor actor) {
        PlatformStaff s = find(id);
        Credential c = credentials.findByStaffId(id).orElseThrow(this::notFound);

        String doc = DocumentValidator.normalize(req.docNumber());
        String phone = blankToNull(req.phone());
        String newEmail = hasText(req.email()) ? ContactValidator.normalizeEmail(req.email()) : s.getEmail();
        boolean emailChanged = !newEmail.equalsIgnoreCase(s.getEmail());
        if (emailChanged && s.getStatus() != AccessStatus.INVITED) {
            throw new Exceptions("error.staff.emailLocked", HttpStatus.CONFLICT);
        }
        validateIdentity(req.docType(), doc, newEmail, phone, id);

        Map<String, Object> changes = new LinkedHashMap<>();
        track(changes, "firstName", s.getFirstName(), req.firstName().trim());
        track(changes, "lastName", s.getLastName(), req.lastName().trim());
        track(changes, "docType", s.getDocType(), req.docType());
        track(changes, "docNumber", s.getDocNumber(), doc);
        track(changes, "email", s.getEmail(), newEmail);
        track(changes, "phone", s.getPhone(), phone);
        track(changes, "position", s.getPosition(), blankToNull(req.position()));
        track(changes, "hireDate", s.getHireDate(), req.hireDate());

        s.setFirstName(req.firstName().trim());
        s.setLastName(req.lastName().trim());
        s.setDocType(req.docType());
        s.setDocNumber(doc);
        s.setEmail(newEmail);
        s.setPhone(phone);
        s.setPosition(blankToNull(req.position()));
        s.setHireDate(req.hireDate());
        staffRepository.saveAndFlush(s);

        if (emailChanged) {
            // el usuario de ingreso es el correo; la invitación anterior (al correo viejo) queda invalidada al emitir la nueva
            c.setUsername(newEmail);
            credentials.saveAndFlush(c);
            invitations.sendInvite(c.getId(), newEmail, s.fullName(), null, locale());
        }
        audit.record(AuditService.Command.of(MODULE, "UPDATE", ENTITY, s.getId(), changes));
        return toResponse(s, c, actor);
    }

    // ---------------------------------------------------------------- rol

    @Transactional
    public StaffResponse changeRole(UUID id, StaffRoleRequest req, AuthenticatedActor actor) {
        PlatformStaff s = find(id);
        assertNotSelf(s, actor);                                                    // [V3]
        if (s.getStaffRole() == req.staffRole()) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, s.getStaffRole());
        }
        if (s.getStaffRole() == StaffRole.SYSTEM_ADMIN && s.getStatus() == AccessStatus.ACTIVE) {
            assertNotLastAdmin();                                                   // [V2]
        }
        StaffRole from = s.getStaffRole();
        s.setStaffRole(req.staffRole());
        staffRepository.saveAndFlush(s);

        Credential c = credentials.findByStaffId(id).orElseThrow(this::notFound);
        refreshTokens.revokeAll(c.getId(), "ROLE_CHANGED");
        accessState.invalidateStaff(id);
        audit.record(AuditService.Command.of(MODULE, "ROLE_CHANGE", ENTITY, id,
                diff("from", from, "to", req.staffRole(), "reason", blankToNull(req.reason()))));
        return toResponse(s, c, actor);
    }

    // ---------------------------------------------------------------- estado

    @Transactional
    public StaffResponse changeStatus(UUID id, StaffStatusRequest req, AuthenticatedActor actor) {
        PlatformStaff s = find(id);
        assertNotSelf(s, actor);                                                    // [V3]
        Credential c = credentials.findByStaffId(id).orElseThrow(this::notFound);
        AccessStatus from = s.getStatus();
        AccessStatus to = req.status();

        if (to != AccessStatus.ACTIVE && to != AccessStatus.INACTIVE) {
            // INVITED lo fija el alta y LOCKED la capa de seguridad: nunca a mano
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, from);
        }
        if (to == AccessStatus.INACTIVE) {
            if (from == AccessStatus.INACTIVE) {
                throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, from);
            }
            if (!hasText(req.reason())) {
                throw new Exceptions("error.common.reasonRequired", HttpStatus.BAD_REQUEST);
            }
            if (s.getStaffRole() == StaffRole.SYSTEM_ADMIN && from == AccessStatus.ACTIVE) {
                assertNotLastAdmin();                                               // [V2]
            }
            s.setStatus(AccessStatus.INACTIVE);
            s.setStatusReason(req.reason().trim());
            c.setStatus(AccessStatus.INACTIVE);
            credentials.saveAndFlush(c);
            refreshTokens.revokeAll(c.getId(), "STAFF_INACTIVATED");
        } else {
            if (from != AccessStatus.INACTIVE) {
                throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, from);
            }
            s.setStatusReason(null);
            boolean neverAccepted = c.getPasswordHash() == null;
            if (neverAccepted) {
                // nunca aceptó la invitación: vuelve a INVITED y se le reenvía el enlace
                s.setStatus(AccessStatus.INVITED);
                c.setStatus(AccessStatus.INVITED);
            } else {
                s.setStatus(AccessStatus.ACTIVE);
                c.setStatus(AccessStatus.ACTIVE);
            }
            c.setFailedAttempts(0);
            c.setLockedUntil(null);
            credentials.saveAndFlush(c);
            if (neverAccepted) {
                invitations.sendInvite(c.getId(), s.getEmail(), s.fullName(), null, locale());
            }
        }
        staffRepository.saveAndFlush(s);
        accessState.invalidateStaff(id);
        audit.record(AuditService.Command.of(MODULE, "STATUS_CHANGE", ENTITY, id,
                diff("from", from, "to", s.getStatus(), "reason", blankToNull(req.reason()))));
        return toResponse(s, c, actor);
    }

    // ---------------------------------------------------------------- invitación

    @Transactional
    public void resendInvite(UUID id) {
        PlatformStaff s = find(id);
        if (s.getStatus() != AccessStatus.INVITED) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, s.getStatus());
        }
        Credential c = credentials.findByStaffId(id).orElseThrow(this::notFound);
        invitations.sendInvite(c.getId(), s.getEmail(), s.fullName(), null, locale());
        audit.record(AuditService.Command.of(MODULE, "INVITE_RESENT", ENTITY, id, diff("email", s.getEmail())));
    }

    // ---------------------------------------------------------------- reglas y utilidades

    /** [V3] Un SYSTEM_ADMIN no cambia su propio rol ni su propio estado. */
    private void assertNotSelf(PlatformStaff target, AuthenticatedActor actor) {
        if (target.getId().equals(actor.ownerId())) {
            throw new Exceptions("error.access.selfChange", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /** [V2] Debe quedar al menos un SYSTEM_ADMIN activo (defensa adicional a V3). */
    private void assertNotLastAdmin() {
        if (staffRepository.countByStaffRoleAndStatus(StaffRole.SYSTEM_ADMIN, AccessStatus.ACTIVE) <= 1) {
            throw new Exceptions("error.access.lastSystemAdmin", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /** [V1] + formato de documento, correo y teléfono (validadores únicos del núcleo 01). */
    private void validateIdentity(DocumentType type, String doc, String email, String phone, UUID excludeId) {
        if (type == DocumentType.RUC || !DocumentValidator.isValid(type, doc)) {
            throw new Exceptions("error.common.docInvalid", HttpStatus.BAD_REQUEST, doc, type);
        }
        if (!ContactValidator.isValidEmail(email)) {
            throw new Exceptions("error.common.emailInvalid", HttpStatus.BAD_REQUEST);
        }
        if (phone != null && !ContactValidator.isValidPhone(phone)) {
            throw new Exceptions("error.common.phoneInvalid", HttpStatus.BAD_REQUEST);
        }
        boolean docTaken = excludeId == null
                ? staffRepository.existsByDocTypeAndDocNumber(type, doc)
                : staffRepository.existsByDocTypeAndDocNumberAndIdNot(type, doc, excludeId);
        if (docTaken) {
            throw new Exceptions("error.staff.docDuplicate", HttpStatus.CONFLICT);
        }
        if (staffRepository.emailTaken(email, excludeId)) {
            throw new Exceptions("error.staff.emailDuplicate", HttpStatus.CONFLICT);
        }
    }

    private PlatformStaff find(UUID id) {
        return staffRepository.findById(id).orElseThrow(this::notFound);
    }

    private Exceptions notFound() {
        return new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
    }

    private StaffResponse toResponse(PlatformStaff s, Credential c, AuthenticatedActor actor) {
        return new StaffResponse(s.getId(), s.getFirstName(), s.getLastName(), s.fullName(), s.getDocType(), s.getDocNumber(),
                s.getEmail(), s.getPhone(), s.getPosition(), s.getStaffRole(), s.getStatus(), s.getStatusReason(),
                s.getHireDate(), s.getLastLoginAt(), c != null && c.isMfaEnabled(),
                c != null && c.isLockedNow(clock.instant()), s.getId().equals(actor.ownerId()),
                s.getCreatedAt(), s.getUpdatedAt());
    }

    /** Solo se ordena por columnas conocidas; cualquier otra cosa cae al orden por fecha de alta. */
    private static String sortField(String field) {
        return switch (field) {
            case "fullName" -> "lastName";
            case "firstName", "lastName", "email", "docNumber", "position", "staffRole", "status", "hireDate", "lastLoginAt",
                 "createdAt" -> field;
            default -> "createdAt";
        };
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, value);
        }
    }

    private static void track(Map<String, Object> changes, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            Map<String, Object> pair = new LinkedHashMap<>();
            pair.put("from", before == null ? null : before.toString());
            pair.put("to", after == null ? null : after.toString());
            changes.put(field, pair);
        }
    }

    private static Map<String, Object> diff(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            if (kv[i + 1] != null) {
                m.put((String) kv[i], kv[i + 1].toString());
            }
        }
        return m;
    }

    private static String locale() {
        return LocaleContextHolder.getLocale().getLanguage();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return hasText(s) ? s.trim() : null;
    }
}
