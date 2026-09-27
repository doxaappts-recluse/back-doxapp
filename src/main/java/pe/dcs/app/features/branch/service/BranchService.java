package pe.dcs.app.features.branch.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.dcs.app.features.branch.domain.Branch;
import pe.dcs.app.features.branch.domain.BranchRepository;
import pe.dcs.app.features.branch.dto.BranchCreateRequest;
import pe.dcs.app.features.branch.dto.BranchResponse;
import pe.dcs.app.features.branch.dto.BranchSearchRequest;
import pe.dcs.app.features.branch.dto.BranchSelfUpdateRequest;
import pe.dcs.app.features.branch.dto.BranchStatusRequest;
import pe.dcs.app.features.branch.dto.BranchUpdateRequest;
import pe.dcs.app.features.branch.dto.PublicBranchResponse;
import pe.dcs.app.features.branch.dto.ScheduleEntryDto;
import pe.dcs.app.features.organization.domain.Organization;
import pe.dcs.app.features.organization.domain.OrganizationRepository;
import pe.dcs.app.features.organization.domain.OrganizationStatus;
import pe.dcs.app.features.organization.dto.AddressDto;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.AccessStateCache;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.features.config.service.SettingsService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.domain.StatusRules;
import pe.dcs.app.shared.image.ImageInspector;
import pe.dcs.app.shared.storage.FileStorageService;
import pe.dcs.app.shared.vo.Address;
import pe.dcs.app.shared.vo.ContactValidator;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.util.pagination.SortRequest;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * M04 · Sedes. N1 (plataforma) crea, edita, inactiva y cambia la principal de las sedes de cada organización;
 * N2/N3 consultan las suyas y editan solo su presentación (nombre visible, logo, contacto, dirección, horarios).
 *
 * <p>Reglas: [V1] nombre 2–100 único por organización · [V2] código {@code [A-Z0-9-]{2,10}} único por organización ·
 * [V3] teléfono/correo válidos · [V4] apertura ≥ fundación · [V5] exactamente una principal ACTIVA, cambiarla es un
 * intercambio atómico y la principal no se inactiva sin nombrar otra · [V6] tope {@code maxBranches} del contrato ·
 * [V7] inactivar exige 0 personas activas y sin contrato por sede vigente · [V8] la organización no está CERRADA ·
 * [V9] N2/N3 no tocan identidad (403) · [V10] sede ajena → 404.
 *
 * <p>Las operaciones que cuentan o cambian la principal bloquean la fila de la organización
 * ({@code OrganizationRepository.lockById}) para que dos peticiones simultáneas no rompan [V5]/[V6].
 */
@Service
@RequiredArgsConstructor
public class BranchService {

    static final String MODULE_N1 = "BRANCHES";
    static final String MODULE_N2 = "BRANCH";
    static final String ENTITY = "Branch";

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9-]{2,10}$");
    private static final Pattern HOUR = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final List<String> DAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    private final BranchRepository branches;
    private final OrganizationRepository organizations;
    private final AuthorizationService authorization;
    private final AccessStateCache accessState;
    private final FileStorageService storage;
    private final AuditService audit;
    private final SettingsService settings;
    private final Clock clock;

    // ---------------------------------------------------------------- N1 · consulta

    @Transactional(readOnly = true)
    public List<BranchResponse> listByOrganization(UUID orgId) {
        Organization o = organization(orgId);
        return branches.findByOrganizationId(orgId).stream()
                .sorted(byMainThenName())
                .map(b -> toResponse(b, o, true, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<BranchResponse> search(UUID orgId, BranchSearchRequest req) {
        Organization o = organization(orgId);
        BranchSearchRequest.Filters f = req == null || req.filters() == null ? new BranchSearchRequest.Filters(null, null) : req.filters();

        Specification<Branch> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("organizationId"), orgId));
            if (hasText(f.q())) {
                String like = "%" + f.q().trim().toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("displayName"), "")), like),
                        cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("address").<String>get("city"), "")), like)));
            }
            if (hasText(f.status())) {
                ps.add(cb.equal(root.get("status"), parseStatus(f.status())));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };

        List<SortRequest> sorts = req == null ? null : req.sorts();
        if (sorts == null || sorts.isEmpty()) {
            sorts = List.of(sort("main", "DESC"), sort("name", "ASC"));
        }
        Pageable pageable = PageableUtil.buildPageable(req == null ? null : req.pagination(), sorts, BranchService::sortField);
        Page<Branch> page = branches.findAll(spec, pageable);
        List<BranchResponse> content = page.getContent().stream().map(b -> toResponse(b, o, true, null)).toList();
        return new PageResponse<>(content, new PaginationResponse(
                (int) page.getTotalElements(), page.getTotalPages(), page.getSize(), page.getNumber()));
    }

    @Transactional(readOnly = true)
    public BranchResponse getPlatform(UUID id) {
        Branch b = branches.findById(id).orElseThrow(BranchService::notFound);
        return toResponse(b, organization(b.getOrganizationId()), true, null);
    }

    // ---------------------------------------------------------------- N1 · alta y edición

    @Transactional
    public BranchResponse create(UUID orgId, BranchCreateRequest req) {
        Organization o = lockOpenOrganization(orgId);

        Branch b = new Branch();
        b.setOrganizationId(orgId);
        applyIdentity(b, o, req.name(), req.code(), req.openingDate(), req.timezone());
        applyPresentation(b, o, req.displayName(), req.phone(), req.email(), req.address(), req.publicSchedule());

        assertRoom(o);                                                                  // [V6]
        // [V5] la primera sede (o la primera tras quedar sin principal activa) es la principal
        b.setMain(!branches.existsByOrganizationIdAndMainTrueAndStatus(orgId, StatusType.ACTIVE));
        b.setStatus(StatusType.ACTIVE);
        b = branches.saveAndFlush(b);

        audit.record(new AuditService.Command(MODULE_N1, "CREATE", ENTITY, b.getId(), orgId, b.getId(),
                diff("name", b.getName(), "code", b.getCode(), "main", b.isMain())));
        return toResponse(b, o, true, null);
    }

    @Transactional
    public BranchResponse update(UUID id, BranchUpdateRequest req) {
        Branch b = lockedBranch(id);
        Organization o = organization(b.getOrganizationId());
        assertNotClosed(o);
        checkVersion(b, req.version());
        Branch before = snapshot(b);

        applyIdentity(b, o, req.name(), req.code(), req.openingDate(), req.timezone());
        applyPresentation(b, o, req.displayName(), req.phone(), req.email(), req.address(), req.publicSchedule());
        branches.saveAndFlush(b);

        Map<String, Object> changes = new LinkedHashMap<>();
        trackAll(changes, before, b);
        audit.record(new AuditService.Command(MODULE_N1, "UPDATE", ENTITY, b.getId(), b.getOrganizationId(), b.getId(), changes));
        return toResponse(b, o, true, null);
    }

    /** Inactivar (con motivo) o reactivar. Nunca hay borrado físico. */
    @Transactional
    public BranchResponse changeStatus(UUID id, BranchStatusRequest req) {
        Branch b = lockedBranch(id);
        Organization o = organization(b.getOrganizationId());
        assertNotClosed(o);                                                              // [V8]
        StatusType from = b.getStatus();
        StatusType to = req.status();
        String reason = blankToNull(req.reason());
        StatusRules.assertTransition(from, to, reason);

        Map<String, Object> extra = new LinkedHashMap<>();
        if (to == StatusType.INACTIVE) {
            Branch newMain = null;
            if (b.isMain()) {                                                             // [V5]
                newMain = requireNewMain(b, req.newMainBranchId());
            }
            long people = branches.activePeople(b.getOrganizationId(), b.getId());        // [V7]
            if (people > 0) {
                throw new Exceptions("error.branch.hasActivePeople", HttpStatus.UNPROCESSABLE_ENTITY, people);
            }
            long contracts = branches.activeBranchContracts(b.getOrganizationId(), b.getId());
            if (contracts > 0) {
                throw new Exceptions("error.branch.hasActiveContract", HttpStatus.UNPROCESSABLE_ENTITY, contracts);
            }
            b.setStatus(StatusType.INACTIVE);
            b.setStatusReason(reason);
            b.setInactivatedAt(clock.instant());
            if (newMain != null) {
                b.setMain(false);
                branches.saveAndFlush(b);          // libera el índice de "una sola principal" antes de asignar la nueva
                newMain.setMain(true);
                branches.saveAndFlush(newMain);
                extra.put("newMain", newMain.getId());
            }
        } else {                                                                          // ACTIVE
            assertRoom(o);                                                                // [V6] también al reactivar
            b.setStatus(StatusType.ACTIVE);
            b.setStatusReason(null);
            b.setInactivatedAt(null);
            if (!branches.existsByOrganizationIdAndMainTrueAndStatus(o.getId(), StatusType.ACTIVE)) {
                b.setMain(true);
                extra.put("becameMain", true);
            }
        }
        branches.saveAndFlush(b);
        accessState.invalidateAll();

        extra.put("from", from);
        extra.put("to", to);
        extra.put("reason", reason);
        audit.record(new AuditService.Command(MODULE_N1, "STATUS_CHANGE", ENTITY, b.getId(), b.getOrganizationId(), b.getId(), diff(extra)));
        return toResponse(b, o, true, null);
    }

    /** [V5] Intercambio atómico de la sede principal: la anterior deja de serlo y esta pasa a serlo en la misma transacción. */
    @Transactional
    public BranchResponse makeMain(UUID id) {
        Branch b = lockedBranch(id);
        Organization o = organization(b.getOrganizationId());
        assertNotClosed(o);
        if (b.getStatus() != StatusType.ACTIVE) {
            throw new Exceptions("error.branch.mainMustBeActive", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (b.isMain()) {
            throw new Exceptions("error.branch.alreadyMain", HttpStatus.CONFLICT);
        }
        Optional<Branch> old = branches.findByOrganizationIdAndMainTrueAndStatus(o.getId(), StatusType.ACTIVE);
        old.ifPresent(prev -> {
            prev.setMain(false);
            branches.saveAndFlush(prev);
        });
        b.setMain(true);
        branches.saveAndFlush(b);

        audit.record(new AuditService.Command(MODULE_N1, "MAIN_CHANGE", ENTITY, b.getId(), o.getId(), b.getId(),
                diff("from", old.map(Branch::getId).orElse(null), "to", b.getId())));
        return toResponse(b, o, true, null);
    }

    // ---------------------------------------------------------------- N2/N3 · sedes de la organización

    @Transactional(readOnly = true)
    public List<BranchResponse> mine(AuthenticatedActor actor, AccessScope scope) {
        Organization o = organization(scope.organizationId());
        boolean canEdit = canEdit(actor, o);
        return branches.findByOrganizationId(o.getId()).stream()
                .filter(b -> scope.canSeeBranch(b.getId()))
                .sorted(byMainThenName())
                .map(b -> toResponse(b, o, false, canEdit))
                .toList();
    }

    @Transactional(readOnly = true)
    public BranchResponse getMine(AuthenticatedActor actor, AccessScope scope, UUID id) {
        Organization o = organization(scope.organizationId());
        return toResponse(visible(scope, id), o, false, canEdit(actor, o));
    }

    @Transactional
    public BranchResponse updateMine(AuthenticatedActor actor, AccessScope scope, UUID id, BranchSelfUpdateRequest req) {
        Branch b = visible(scope, id);
        Organization o = organization(scope.organizationId());
        assertEditable(actor, o, b);
        rejectIdentityChanges(b, req);                                                    // [V9]
        checkVersion(b, req.version());
        Branch before = snapshot(b);

        applyPresentation(b, o, req.displayName(), req.phone(), req.email(), req.address(), req.publicSchedule());
        branches.saveAndFlush(b);

        Map<String, Object> changes = new LinkedHashMap<>();
        trackAll(changes, before, b);
        audit.record(new AuditService.Command(MODULE_N2, "UPDATE", ENTITY, b.getId(), b.getOrganizationId(), b.getId(), changes));
        return toResponse(b, o, false, true);
    }

    @Transactional
    public BranchResponse uploadLogo(AuthenticatedActor actor, AccessScope scope, UUID id, MultipartFile file) {
        Branch b = visible(scope, id);
        Organization o = organization(scope.organizationId());
        assertEditable(actor, o, b);
        if (file == null || file.isEmpty()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "file");
        }
        if (file.getSize() > ImageInspector.MAX_BYTES) {
            throw new Exceptions("error.common.fileSize", HttpStatus.BAD_REQUEST, "512 KB");
        }
        ImageInspector.Inspected img;
        try {
            img = ImageInspector.inspect(file.getBytes());
        } catch (ImageInspector.InvalidImageException ex) {
            throw switch (ex.getMessage()) {
                case "size" -> new Exceptions("error.common.fileSize", HttpStatus.BAD_REQUEST, "512 KB");
                case "type" -> new Exceptions("error.common.fileType", HttpStatus.BAD_REQUEST, "PNG, WEBP, SVG");
                default -> new Exceptions("error.org.logoInvalid", HttpStatus.BAD_REQUEST);
            };
        } catch (IOException ex) {
            throw new Exceptions("error.common.storage", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        int rev = b.getLogoRevision() + 1;
        String oldKey = b.getLogoKey();
        String newKey = "org/" + b.getOrganizationId() + "/branches/" + b.getId() + "/logo-" + rev + "." + img.kind().extension;
        storage.put(newKey, img.data(), img.kind().contentType);
        b.setLogoKey(newKey);
        b.setLogoRevision(rev);
        branches.saveAndFlush(b);
        if (oldKey != null && !oldKey.equals(newKey)) {
            storage.delete(oldKey);
        }
        audit.record(new AuditService.Command(MODULE_N2, "LOGO_UPLOAD", ENTITY, b.getId(), b.getOrganizationId(), b.getId(),
                diff("type", img.kind().name(), "bytes", img.data().length)));
        return toResponse(b, o, false, true);
    }

    @Transactional
    public BranchResponse deleteLogo(AuthenticatedActor actor, AccessScope scope, UUID id) {
        Branch b = visible(scope, id);
        Organization o = organization(scope.organizationId());
        assertEditable(actor, o, b);
        if (b.getLogoKey() != null) {
            String old = b.getLogoKey();
            b.setLogoKey(null);
            b.setLogoRevision(b.getLogoRevision() + 1);
            branches.saveAndFlush(b);
            storage.delete(old);
            audit.record(new AuditService.Command(MODULE_N2, "LOGO_DELETE", ENTITY, b.getId(), b.getOrganizationId(), b.getId(), Map.of()));
        }
        return toResponse(b, o, false, true);
    }

    // ---------------------------------------------------------------- público (sin sesión)

    /** Sedes ACTIVAS de una organización visible (no borrador ni cerrada): solo nombre, dirección, horarios y contacto. */
    @Transactional(readOnly = true)
    public Optional<List<PublicBranchResponse>> publicList(String rawSlug) {
        return publicOrganization(rawSlug).map(o -> branches.findByOrganizationId(o.getId()).stream()
                .filter(b -> b.getStatus() == StatusType.ACTIVE)
                .sorted(byMainThenName())
                .map(b -> new PublicBranchResponse(displayOf(b), toDto(b.getAddress()), b.getPhone(), b.getEmail(),
                        logoUrl(o, b), schedule(b)))
                .toList());
    }

    @Transactional(readOnly = true)
    public Optional<FileStorageService.StoredFile> publicLogo(String rawSlug, String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        return publicOrganization(rawSlug)
                .flatMap(o -> branches.findByOrganizationId(o.getId()).stream()
                        .filter(b -> b.getStatus() == StatusType.ACTIVE && b.getCode().equals(code))
                        .findFirst())
                .map(Branch::getLogoKey)
                .flatMap(storage::get);
    }

    private Optional<Organization> publicOrganization(String rawSlug) {
        return organizations.findBySlug(rawSlug == null ? "" : rawSlug.trim().toLowerCase(Locale.ROOT))
                .filter(o -> o.getStatus() != OrganizationStatus.DRAFT && o.getStatus() != OrganizationStatus.CLOSED);
    }

    // ---------------------------------------------------------------- reglas

    private void applyIdentity(Branch b, Organization o, String name, String code, java.time.LocalDate opening, String timezone) {
        String n = name == null ? "" : name.trim();
        if (n.length() < 2 || n.length() > 100) {
            throw new Exceptions("error.branch.nameLength", HttpStatus.BAD_REQUEST);        // [V1]
        }
        if (branches.nameTaken(o.getId(), n, b.getId())) {
            throw new Exceptions("error.branch.nameTaken", HttpStatus.CONFLICT);
        }
        String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(c).matches()) {
            throw new Exceptions("error.branch.codeFormat", HttpStatus.BAD_REQUEST);        // [V2]
        }
        if (branches.codeTaken(o.getId(), c, b.getId())) {
            throw new Exceptions("error.branch.codeTaken", HttpStatus.CONFLICT);
        }
        if (opening != null && o.getFoundedDate() != null && opening.isBefore(o.getFoundedDate())) {
            throw new Exceptions("error.branch.openingBeforeFounded", HttpStatus.UNPROCESSABLE_ENTITY);   // [V4]
        }
        String tz = blankToNull(timezone);
        if (tz != null && !ZoneId.getAvailableZoneIds().contains(tz)) {
            throw new Exceptions("error.org.timezoneInvalid", HttpStatus.BAD_REQUEST);
        }
        b.setName(n);
        b.setCode(c);
        b.setOpeningDate(opening);
        b.setTimezone(tz);
    }

    private void applyPresentation(Branch b, Organization o, String displayName, String phone, String email,
                                   AddressDto address, List<ScheduleEntryDto> schedule) {
        String display = blankToNull(displayName);
        if (display != null && (display.length() < 2 || display.length() > 60)) {
            throw new Exceptions("error.org.displayNameLength", HttpStatus.BAD_REQUEST);
        }
        String mail = hasText(email) ? ContactValidator.normalizeEmail(email) : null;      // [V3]
        if (mail != null && !ContactValidator.isValidEmail(mail)) {
            throw new Exceptions("error.common.emailInvalid", HttpStatus.BAD_REQUEST);
        }
        String tel = blankToNull(phone);
        if (tel != null && !ContactValidator.isValidPhone(tel)) {
            throw new Exceptions("error.common.phoneInvalid", HttpStatus.BAD_REQUEST);
        }
        b.setDisplayName(display);
        b.setEmail(mail);
        b.setPhone(tel);

        Address a = new Address();
        if (address != null) {
            a.setLine(blankToNull(address.line()));
            a.setDistrict(blankToNull(address.district()));
            a.setCity(blankToNull(address.city()));
            a.setRegion(blankToNull(address.region()));
            a.setReference(blankToNull(address.reference()));
            String ac = blankToNull(address.country());
            if (ac != null) {
                ac = ac.toUpperCase(Locale.ROOT);
                if (!Set.of(Locale.getISOCountries()).contains(ac)) {
                    throw new Exceptions("error.org.countryInvalid", HttpStatus.BAD_REQUEST);
                }
            }
            a.setCountry(ac);
        }
        b.setAddress(a);
        b.setPublicSchedule(normalizeSchedule(schedule, settings.getInt(b.getOrganizationId(), b.getId(), "BRANCH", "publicScheduleMax")));
    }

    /** Valida y ordena los horarios públicos (día y hora de inicio). */
    static List<Map<String, String>> normalizeSchedule(List<ScheduleEntryDto> in, int max) {
        List<Map<String, String>> out = new ArrayList<>();
        if (in == null || in.isEmpty()) {
            return out;
        }
        if (in.size() > max) {
            throw new Exceptions("error.branch.scheduleTooMany", HttpStatus.BAD_REQUEST, max);
        }
        int row = 0;
        for (ScheduleEntryDto e : in) {
            row++;
            String day = e == null || e.day() == null ? "" : e.day().trim().toUpperCase(Locale.ROOT);
            String from = e == null || e.from() == null ? "" : e.from().trim();
            String to = e == null || e.to() == null ? "" : e.to().trim();
            if (!DAYS.contains(day) || !HOUR.matcher(from).matches() || !HOUR.matcher(to).matches()
                    || !LocalTime.parse(from).isBefore(LocalTime.parse(to))) {
                throw new Exceptions("error.branch.scheduleInvalid", HttpStatus.BAD_REQUEST, row);
            }
            Map<String, String> m = new LinkedHashMap<>();
            m.put("day", day);
            m.put("from", from);
            m.put("to", to);
            String label = blankToNull(e.label());
            if (label != null) {
                m.put("label", label);
            }
            out.add(m);
        }
        out.sort(Comparator.<Map<String, String>>comparingInt(m -> DAYS.indexOf(m.get("day"))).thenComparing(m -> m.get("from")));
        return out;
    }

    /** [V6] Hay cupo para otra sede ACTIVA según el {@code maxBranches} del contrato vigente (si fija uno). */
    private void assertRoom(Organization o) {
        Integer max = branches.contractMaxBranches(o.getId());
        if (max != null && branches.countByOrganizationIdAndStatus(o.getId(), StatusType.ACTIVE) >= max) {
            throw new Exceptions("error.branch.maxReached", HttpStatus.UNPROCESSABLE_ENTITY, max);
        }
    }

    /** [V5] Sede que pasará a ser la principal cuando se inactiva la actual: distinta, de la misma organización y ACTIVA. */
    private Branch requireNewMain(Branch current, UUID newMainId) {
        if (newMainId == null) {
            throw new Exceptions("error.branch.mainCannotInactivate", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Branch candidate = branches.findByIdAndOrganizationId(newMainId, current.getOrganizationId()).orElse(null);
        if (candidate == null || candidate.getId().equals(current.getId()) || candidate.getStatus() != StatusType.ACTIVE) {
            throw new Exceptions("error.branch.mainRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return candidate;
    }

    /** [V9] Lo que N2/N3 no pueden cambiar: si llega con un valor distinto al vigente, 403. */
    private static void rejectIdentityChanges(Branch b, BranchSelfUpdateRequest req) {
        boolean tampered =
                (req.name() != null && !req.name().trim().equals(b.getName()))
                || (req.code() != null && !req.code().trim().equalsIgnoreCase(b.getCode()))
                || (req.main() != null && req.main() != b.isMain())
                || (req.status() != null && !req.status().trim().equalsIgnoreCase(b.getStatus().name()))
                || (req.openingDate() != null && !req.openingDate().equals(b.getOpeningDate()))
                || (req.timezone() != null && !Objects.equals(blankToNull(req.timezone()), b.getTimezone()));
        if (tampered) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
    }

    private void assertEditable(AuthenticatedActor actor, Organization o, Branch b) {
        assertNotClosed(o);
        if (!canEdit(actor, o)) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        if (b.getStatus() != StatusType.ACTIVE) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, b.getStatus());
        }
    }

    private boolean canEdit(AuthenticatedActor actor, Organization o) {
        return o.getStatus() != OrganizationStatus.CLOSED && authorization.effectiveActions(actor, MODULE_N2).contains("E");
    }

    private static void checkVersion(Branch b, Long version) {
        if (version != null && !version.equals(b.getVersion())) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
    }

    private void assertNotClosed(Organization o) {
        if (o.getStatus() == OrganizationStatus.CLOSED) {
            throw new Exceptions("error.branch.orgClosed", HttpStatus.CONFLICT);           // [V8]
        }
    }

    // ---------------------------------------------------------------- acceso a datos

    private Organization organization(UUID id) {
        return organizations.findById(id).orElseThrow(BranchService::notFound);
    }

    /** Bloquea la organización (serializa altas y cambios de principal) y comprueba que no esté cerrada. */
    private Organization lockOpenOrganization(UUID orgId) {
        Organization o = organizations.lockById(orgId).orElseThrow(BranchService::notFound);
        assertNotClosed(o);
        return o;
    }

    /** Bloquea la organización de la sede y recién entonces lee la sede (así no se lee un estado ya cambiado). */
    private Branch lockedBranch(UUID id) {
        UUID orgId = branches.organizationIdOf(id).orElseThrow(BranchService::notFound);
        organizations.lockById(orgId).orElseThrow(BranchService::notFound);
        return branches.findById(id).orElseThrow(BranchService::notFound);
    }

    /** [V10] Sede de la organización actual y dentro del alcance; cualquier otra → 404 (no se revela que exista). */
    private Branch visible(AccessScope scope, UUID id) {
        return branches.findByIdAndOrganizationId(id, scope.organizationId())
                .filter(b -> scope.canSeeBranch(b.getId()))
                .orElseThrow(BranchService::notFound);
    }

    private static Exceptions notFound() {
        return new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
    }

    // ---------------------------------------------------------------- mapeo

    private BranchResponse toResponse(Branch b, Organization o, boolean platform, Boolean canEditMine) {
        String tz = b.getTimezone();
        return new BranchResponse(b.getId(), o.getId(), o.getName(), o.getSlug(), b.getName(), b.getCode(), b.isMain(),
                b.getStatus(), b.getStatusReason(), b.getInactivatedAt(), b.getDisplayName(), displayOf(b),
                toDto(b.getAddress()), b.getPhone(), b.getEmail(), b.getOpeningDate(), tz, tz != null ? tz : o.getTimezone(),
                logoUrl(o, b), schedule(b),
                platform ? o.getStatus() != OrganizationStatus.CLOSED : Boolean.TRUE.equals(canEditMine) && b.getStatus() == StatusType.ACTIVE,
                platform && o.getStatus() != OrganizationStatus.CLOSED,
                platform ? branches.activePeople(o.getId(), b.getId()) : null,
                platform ? branches.activeBranchContracts(o.getId(), b.getId()) : null,
                b.getCreatedAt(), b.getUpdatedAt(), b.getVersion());
    }

    private static String displayOf(Branch b) {
        return b.getDisplayName() != null ? b.getDisplayName() : b.getName();
    }

    private static String logoUrl(Organization o, Branch b) {
        return b.getLogoKey() == null ? null
                : "/api/v1/public/orgs/" + o.getSlug() + "/branches/" + b.getCode() + "/logo?v=" + b.getLogoRevision();
    }

    private static List<ScheduleEntryDto> schedule(Branch b) {
        List<Map<String, String>> raw = b.getPublicSchedule() == null ? List.of() : b.getPublicSchedule();
        return raw.stream().map(m -> new ScheduleEntryDto(m.get("day"), m.get("from"), m.get("to"), m.get("label"))).toList();
    }

    private static AddressDto toDto(Address a) {
        return a == null ? new AddressDto(null, null, null, null, null, null)
                : new AddressDto(a.getLine(), a.getDistrict(), a.getCity(), a.getRegion(), a.getCountry(), a.getReference());
    }

    // ---------------------------------------------------------------- utilidades

    private static Comparator<Branch> byMainThenName() {
        return Comparator.comparing(Branch::isMain).reversed()
                .thenComparing(b -> b.getName().toLowerCase(Locale.ROOT));
    }

    private static SortRequest sort(String key, String direction) {
        SortRequest s = new SortRequest();
        s.setKey(key);
        s.setDirection(direction);
        return s;
    }

    private static String sortField(String field) {
        return switch (field) {
            case "name", "code", "status", "main", "createdAt", "openingDate" -> field;
            default -> "name";
        };
    }

    private static StatusType parseStatus(String value) {
        try {
            return StatusType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, value);
        }
    }

    private static Branch snapshot(Branch b) {
        Branch c = new Branch();
        c.setName(b.getName());
        c.setCode(b.getCode());
        c.setOpeningDate(b.getOpeningDate());
        c.setTimezone(b.getTimezone());
        c.setDisplayName(b.getDisplayName());
        c.setPhone(b.getPhone());
        c.setEmail(b.getEmail());
        Address a = b.getAddress() == null ? new Address() : b.getAddress();
        c.setAddress(new Address(a.getLine(), a.getDistrict(), a.getCity(), a.getRegion(), a.getCountry(), a.getReference()));
        c.setPublicSchedule(b.getPublicSchedule() == null ? List.of() : new ArrayList<>(b.getPublicSchedule()));
        return c;
    }

    private static void trackAll(Map<String, Object> ch, Branch b, Branch a) {
        track(ch, "name", b.getName(), a.getName());
        track(ch, "code", b.getCode(), a.getCode());
        track(ch, "openingDate", b.getOpeningDate(), a.getOpeningDate());
        track(ch, "timezone", b.getTimezone(), a.getTimezone());
        track(ch, "displayName", b.getDisplayName(), a.getDisplayName());
        track(ch, "phone", b.getPhone(), a.getPhone());
        track(ch, "email", b.getEmail(), a.getEmail());
        Address x = b.getAddress(), y = a.getAddress();
        track(ch, "address.line", x.getLine(), y.getLine());
        track(ch, "address.district", x.getDistrict(), y.getDistrict());
        track(ch, "address.city", x.getCity(), y.getCity());
        track(ch, "address.region", x.getRegion(), y.getRegion());
        track(ch, "address.country", x.getCountry(), y.getCountry());
        track(ch, "address.reference", x.getReference(), y.getReference());
        track(ch, "publicSchedule", b.getPublicSchedule(), a.getPublicSchedule());
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

    private static Map<String, Object> diff(Map<String, Object> src) {
        Map<String, Object> m = new LinkedHashMap<>();
        src.forEach((k, v) -> {
            if (v != null) {
                m.put(k, v.toString());
            }
        });
        return m;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return hasText(s) ? s.trim() : null;
    }
}
