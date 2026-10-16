package pe.dcs.app.features.organization.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.access.domain.AccessStatus;
import pe.dcs.app.features.access.repo.UserAccessRepository;
import pe.dcs.app.features.branch.domain.BranchRepository;
import pe.dcs.app.features.contract.domain.Contract;
import pe.dcs.app.features.contract.domain.ContractRepository;
import pe.dcs.app.features.contract.domain.ContractStatus;
import pe.dcs.app.features.organization.domain.Organization;
import pe.dcs.app.features.organization.domain.OrganizationRepository;
import pe.dcs.app.features.organization.domain.OrganizationStatus;
import pe.dcs.app.features.organization.domain.SlugRedirect;
import pe.dcs.app.features.organization.domain.SlugRedirectRepository;
import pe.dcs.app.features.organization.dto.AddressDto;
import pe.dcs.app.features.organization.dto.MyOrganizationResponse;
import pe.dcs.app.features.organization.dto.OrgCreateRequest;
import pe.dcs.app.features.organization.dto.OrgResponse;
import pe.dcs.app.features.organization.dto.OrgSearchRequest;
import pe.dcs.app.features.organization.dto.OrgSelfUpdateRequest;
import pe.dcs.app.features.organization.dto.OrgSlugRequest;
import pe.dcs.app.features.organization.dto.OrgStatusRequest;
import pe.dcs.app.features.organization.dto.OrgUpdateRequest;
import pe.dcs.app.features.organization.dto.SlugCheckResponse;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessStateCache;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.vo.Address;
import pe.dcs.app.shared.vo.ContactValidator;
import pe.dcs.app.shared.vo.DocumentType;
import pe.dcs.app.shared.vo.DocumentValidator;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.util.pagination.SortRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Currency;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * M02 · Organizaciones. N1 (plataforma) las crea, edita y gestiona su ciclo de vida; N2 (ORG_ADMIN) edita solo sus datos
 * de contacto; N3 (ORG_BRANCH_ADMIN) las ve sin datos legales.
 *
 * <p>Ciclo de vida: DRAFT → (TRIAL) → ACTIVE ⇄ SUSPENDED → CLOSED (terminal). Reglas: [V2] RUC válido y único ·
 * [V3] slug (ver {@link SlugPolicy}) · [V6] activar exige contrato ACTIVE + sede principal activa + un ORG_ADMIN ·
 * [V7] cerrar exige confirmar el slug y no tener contrato ACTIVE · [V8] cambio de slug redirige 90 días ·
 * [V13] el mes de inicio fiscal no cambia si hay un periodo cerrado.
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    static final String MODULE_N1 = "ORGANIZATIONS";
    static final String MODULE_N2 = "ORGANIZATION";
    static final String ENTITY = "Organization";
    static final int REDIRECT_DAYS = 90;
    static final int RETENTION_DAYS = 90;
    static final int DEFAULT_TRIAL_DAYS = 30;

    private static final Map<OrganizationStatus, List<OrganizationStatus>> TRANSITIONS = new EnumMap<>(OrganizationStatus.class);

    static {
        TRANSITIONS.put(OrganizationStatus.DRAFT, List.of(OrganizationStatus.TRIAL, OrganizationStatus.ACTIVE, OrganizationStatus.CLOSED));
        TRANSITIONS.put(OrganizationStatus.TRIAL, List.of(OrganizationStatus.ACTIVE, OrganizationStatus.SUSPENDED, OrganizationStatus.CLOSED));
        TRANSITIONS.put(OrganizationStatus.ACTIVE, List.of(OrganizationStatus.SUSPENDED, OrganizationStatus.CLOSED));
        TRANSITIONS.put(OrganizationStatus.SUSPENDED, List.of(OrganizationStatus.ACTIVE, OrganizationStatus.CLOSED));
        TRANSITIONS.put(OrganizationStatus.CLOSED, List.of());
    }

    private final OrganizationRepository organizations;
    private final SlugRedirectRepository redirects;
    private final BranchRepository branches;
    private final ContractRepository contracts;
    private final UserAccessRepository accesses;
    private final SlugPolicy slugPolicy;
    private final FiscalPeriodPort fiscalPeriods;
    private final AccessStateCache accessState;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final Clock clock;

    // ---------------------------------------------------------------- N1 · consulta

    @Transactional(readOnly = true)
    public PageResponse<OrgResponse> search(OrgSearchRequest req) {
        OrgSearchRequest.Filters f = req == null || req.filters() == null
                ? new OrgSearchRequest.Filters(null, null, null) : req.filters();

        Specification<Organization> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (hasText(f.q())) {
                String like = "%" + f.q().trim().toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("legalName"), "")), like),
                        cb.like(cb.coalesce(root.<String>get("taxId"), ""), like),
                        cb.like(cb.lower(root.get("slug")), like)));
            }
            if (hasText(f.status())) {
                ps.add(cb.equal(root.get("status"), parseEnum(OrganizationStatus.class, f.status())));
            }
            if (hasText(f.country())) {
                ps.add(cb.equal(root.get("country"), f.country().trim().toUpperCase()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };

        List<SortRequest> sorts = req == null ? null : req.sorts();
        if (sorts == null || sorts.isEmpty()) {
            SortRequest byCreated = new SortRequest();
            byCreated.setKey("createdAt");
            byCreated.setDirection("DESC");
            sorts = List.of(byCreated);
        }
        Pageable pageable = PageableUtil.buildPageable(req == null ? null : req.pagination(), sorts, OrganizationService::sortField);
        Page<Organization> page = organizations.findAll(spec, pageable);
        List<OrgResponse> content = page.getContent().stream().map(this::toResponse).toList();
        return new PageResponse<>(content, new PaginationResponse(
                (int) page.getTotalElements(), page.getTotalPages(), page.getSize(), page.getNumber()));
    }

    @Transactional(readOnly = true)
    public OrgResponse get(UUID id) {
        return toResponse(find(id));
    }

    /** Comprobación en vivo del identificador mientras se escribe (no reserva nada). */
    @Transactional(readOnly = true)
    public SlugCheckResponse checkSlug(String slug, UUID excludeOrganizationId) {
        String normalized = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
        try {
            slugPolicy.check(normalized, excludeOrganizationId);
            return new SlugCheckResponse(normalized, true, null, null);
        } catch (Exceptions ex) {
            return new SlugCheckResponse(normalized, false, ex.getCode(), ex.getMessage());
        }
    }

    // ---------------------------------------------------------------- N1 · alta y edición

    @Transactional
    public OrgResponse create(OrgCreateRequest req) {
        String slug = slugPolicy.check(req.slug(), null);

        Organization o = new Organization();
        o.setSlug(slug);
        applyLegal(o, req.name(), req.legalName(), req.taxId(), true);
        applyRegional(o, req.country(), req.timezone(), req.currency(), req.defaultLanguage(), req.fiscalYearStartMonth(), true);
        applyContact(o, req.email(), req.phone(), req.foundedDate(), req.address());
        o.setStatus(OrganizationStatus.DRAFT);
        o = organizations.saveAndFlush(o);

        audit.record(new AuditService.Command(MODULE_N1, "CREATE", ENTITY, o.getId(), o.getId(), null,
                diff("name", o.getName(), "slug", o.getSlug(), "taxId", o.getTaxId(), "country", o.getCountry())));
        return toResponse(o);
    }

    @Transactional
    public OrgResponse update(UUID id, OrgUpdateRequest req) {
        Organization o = find(id);
        assertNotClosed(o);
        Map<String, Object> changes = new LinkedHashMap<>();
        Organization before = snapshot(o);

        applyLegal(o, req.name(), req.legalName(), req.taxId(), false);
        applyRegional(o, req.country(), req.timezone(), req.currency(), req.defaultLanguage(), req.fiscalYearStartMonth(), false);
        applyContact(o, req.email(), req.phone(), req.foundedDate(), req.address());
        organizations.saveAndFlush(o);

        trackAll(changes, before, o);
        audit.record(new AuditService.Command(MODULE_N1, "UPDATE", ENTITY, o.getId(), o.getId(), null, changes));
        return toResponse(o);
    }

    // ---------------------------------------------------------------- N1 · slug

    /** [V3][V8] Cambia el slug y deja el anterior redirigiendo 90 días. */
    @Transactional
    public OrgResponse changeSlug(UUID id, OrgSlugRequest req) {
        Organization o = find(id);
        assertNotClosed(o);
        String slug = slugPolicy.check(req.slug(), o.getId());
        String old = o.getSlug();
        if (slug.equalsIgnoreCase(old)) {
            throw new Exceptions("error.org.slugSame", HttpStatus.CONFLICT);
        }

        Instant now = clock.instant();
        // si vuelve a un slug propio antiguo, ya no hace falta su redirección
        redirects.deleteByOldSlugAndOrganizationId(slug, o.getId());
        o.setSlug(slug);
        organizations.saveAndFlush(o);

        SlugRedirect r = redirects.findById(old.toLowerCase()).orElseGet(SlugRedirect::new);
        r.setOldSlug(old.toLowerCase());
        r.setOrganizationId(o.getId());
        r.setCreatedAt(now);
        r.setUntilAt(now.plus(REDIRECT_DAYS, ChronoUnit.DAYS));
        redirects.saveAndFlush(r);

        audit.record(new AuditService.Command(MODULE_N1, "SLUG_CHANGE", ENTITY, o.getId(), o.getId(), null,
                diff("from", old, "to", slug, "redirectUntil", r.getUntilAt())));
        return toResponse(o);
    }

    // ---------------------------------------------------------------- N1 · ciclo de vida

    @Transactional
    public OrgResponse changeStatus(UUID id, OrgStatusRequest req) {
        Organization o = find(id);
        OrganizationStatus from = o.getStatus();
        OrganizationStatus to = req.status();
        if (!TRANSITIONS.get(from).contains(to)) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, from);
        }
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(o.getTimezone())));
        String reason = blankToNull(req.reason());
        Map<String, Object> extra = new LinkedHashMap<>();

        switch (to) {
            case TRIAL -> {
                LocalDate end = req.trialEndsOn() != null ? req.trialEndsOn() : today.plusDays(DEFAULT_TRIAL_DAYS);
                if (!end.isAfter(today)) {
                    throw new Exceptions("error.org.trialInvalid", HttpStatus.BAD_REQUEST);
                }
                o.setTrialEndsAt(end.plusDays(1).atStartOfDay(ZoneId.of(o.getTimezone())).toInstant().minusSeconds(1));
                o.setStatusReason(null);
                extra.put("trialEndsOn", end);
            }
            case ACTIVE -> {
                if (from == OrganizationStatus.DRAFT || from == OrganizationStatus.TRIAL) {
                    OrgResponse.Activation act = activation(o);
                    if (!act.ready()) {                                                   // [V6]
                        throw new Exceptions("error.org.activateRequirements", HttpStatus.UNPROCESSABLE_ENTITY);
                    }
                }
                if (o.getActivatedAt() == null) {
                    o.setActivatedAt(now);
                }
                o.setTrialEndsAt(null);
                o.setStatusReason(null);
            }
            case SUSPENDED -> {
                if (reason == null) {
                    throw new Exceptions("error.common.reasonRequired", HttpStatus.BAD_REQUEST);
                }
                o.setStatusReason(reason);
            }
            case CLOSED -> {
                if (!o.getSlug().equalsIgnoreCase(req.confirmSlug() == null ? "" : req.confirmSlug().trim())) {
                    throw new Exceptions("error.org.closeConfirm", HttpStatus.BAD_REQUEST);
                }
                if (contracts.existsActive(o.getId())) {                                  // [V7]
                    throw new Exceptions("error.org.closeWithContract", HttpStatus.UNPROCESSABLE_ENTITY);
                }
                o.setClosedAt(now);
                o.setRetentionUntil(today.plusDays(RETENTION_DAYS));
                o.setStatusReason(reason);
                extra.put("retentionUntil", o.getRetentionUntil());
            }
            default -> throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, from);
        }
        o.setStatus(to);
        organizations.saveAndFlush(o);

        // suspender/cerrar/reactivar cambia quién puede ingresar: se invalida la caché de accesos (≤ 15 s)
        accessState.invalidateAll();

        extra.put("from", from);
        extra.put("to", to);
        extra.put("reason", reason);
        audit.record(new AuditService.Command(MODULE_N1, "STATUS_CHANGE", ENTITY, o.getId(), o.getId(), null, diff(extra)));
        return toResponse(o);
    }

    // ---------------------------------------------------------------- N2/N3 · "Mi organización"

    @Transactional(readOnly = true)
    public MyOrganizationResponse mine(AuthenticatedActor actor) {
        Organization o = find(requireOrg(actor));
        boolean admin = actor.actsAsOrgAdmin();
        // canEdit = ORG_ADMIN (o acceso asistido, M22 D3) con acción E vigente: sin contrato ACTIVE queda en modo limitado (solo lectura)
        boolean canEdit = admin && authorization.effectiveActions(actor, MODULE_N2).contains("E");
        return toMine(o, admin, canEdit);
    }

    @Transactional
    public MyOrganizationResponse updateMine(OrgSelfUpdateRequest req, AuthenticatedActor actor) {
        if (!actor.actsAsOrgAdmin()) {
            // ORG_BRANDING/ORGANIZATION declaran N3 con acción E: la restricción real (solo ORG_ADMIN, o acceso
            // asistido de M22 D3 con ORGANIZATION en su alcance) se aplica aquí
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        Organization o = find(requireOrg(actor));
        assertNotClosed(o);
        Organization before = snapshot(o);

        String lang = normalizeLanguage(req.defaultLanguage());
        o.setDefaultLanguage(lang);
        applyFiscalMonth(o, req.fiscalYearStartMonth());
        applyContact(o, req.email(), req.phone(), req.foundedDate(), req.address());
        organizations.saveAndFlush(o);

        Map<String, Object> changes = new LinkedHashMap<>();
        trackAll(changes, before, o);
        audit.record(new AuditService.Command(MODULE_N2, "UPDATE", ENTITY, o.getId(), o.getId(), null, changes));
        return toMine(o, true, true);
    }

    // ---------------------------------------------------------------- reglas

    private void applyLegal(Organization o, String name, String legalName, String taxId, boolean creating) {
        String n = name == null ? "" : name.trim();
        if (n.length() < 3 || n.length() > 120) {
            throw new Exceptions("error.org.nameLength", HttpStatus.BAD_REQUEST);
        }
        o.setName(n);
        o.setLegalName(blankToNull(legalName));

        String ruc = hasText(taxId) ? DocumentValidator.normalize(taxId) : null;
        if (ruc != null) {
            if (!DocumentValidator.isValid(DocumentType.RUC, ruc)) {                      // [V2]
                throw new Exceptions("error.org.taxIdInvalid", HttpStatus.BAD_REQUEST);
            }
            if (organizations.taxIdTaken(ruc, creating ? null : o.getId())) {
                throw new Exceptions("error.org.taxIdTaken", HttpStatus.CONFLICT);
            }
        }
        o.setTaxId(ruc);
    }

    private void applyRegional(Organization o, String country, String timezone, String currency, String language,
                               Integer fiscalMonth, boolean creating) {
        String c = hasText(country) ? country.trim().toUpperCase() : (creating ? "PE" : o.getCountry());
        if (!Set.of(Locale.getISOCountries()).contains(c)) {
            throw new Exceptions("error.org.countryInvalid", HttpStatus.BAD_REQUEST);
        }
        o.setCountry(c);

        String tz = hasText(timezone) ? timezone.trim() : (creating ? "America/Lima" : o.getTimezone());
        if (!ZoneId.getAvailableZoneIds().contains(tz)) {
            throw new Exceptions("error.org.timezoneInvalid", HttpStatus.BAD_REQUEST);
        }
        o.setTimezone(tz);

        String cur = hasText(currency) ? currency.trim().toUpperCase() : (creating ? "PEN" : o.getCurrency());
        try {
            Currency.getInstance(cur);
        } catch (IllegalArgumentException ex) {
            throw new Exceptions("error.org.currencyInvalid", HttpStatus.BAD_REQUEST);
        }
        o.setCurrency(cur);

        o.setDefaultLanguage(hasText(language) ? normalizeLanguage(language) : (creating ? "es" : o.getDefaultLanguage()));
        if (creating) {
            o.setFiscalYearStartMonth((short) (fiscalMonth == null ? 1 : fiscalMonth));
        } else {
            applyFiscalMonth(o, fiscalMonth);
        }
    }

    /** [V13] no se cambia el mes de inicio fiscal si ya hay un periodo cerrado. */
    private void applyFiscalMonth(Organization o, Integer month) {
        if (month == null || month.intValue() == o.getFiscalYearStartMonth().intValue()) {
            return;
        }
        if (fiscalPeriods.hasClosedPeriod(o.getId())) {
            throw new Exceptions("error.org.fiscalYearLocked", HttpStatus.CONFLICT);
        }
        o.setFiscalYearStartMonth(month.shortValue());
    }

    private void applyContact(Organization o, String email, String phone, LocalDate founded, AddressDto address) {
        String mail = hasText(email) ? ContactValidator.normalizeEmail(email) : null;
        if (mail != null && !ContactValidator.isValidEmail(mail)) {
            throw new Exceptions("error.common.emailInvalid", HttpStatus.BAD_REQUEST);
        }
        String tel = blankToNull(phone);
        if (tel != null && !ContactValidator.isValidPhone(tel)) {
            throw new Exceptions("error.common.phoneInvalid", HttpStatus.BAD_REQUEST);
        }
        if (founded != null && founded.isAfter(LocalDate.now(clock.withZone(ZoneId.of(o.getTimezone()))))) {
            throw new Exceptions("error.common.futureDate", HttpStatus.BAD_REQUEST);
        }
        o.setEmail(mail);
        o.setPhone(tel);
        o.setFoundedDate(founded);

        Address a = new Address();
        if (address != null) {
            a.setLine(blankToNull(address.line()));
            a.setDistrict(blankToNull(address.district()));
            a.setCity(blankToNull(address.city()));
            a.setRegion(blankToNull(address.region()));
            a.setReference(blankToNull(address.reference()));
            String ac = blankToNull(address.country());
            if (ac != null) {
                ac = ac.toUpperCase();
                if (!Set.of(Locale.getISOCountries()).contains(ac)) {
                    throw new Exceptions("error.org.countryInvalid", HttpStatus.BAD_REQUEST);
                }
            }
            a.setCountry(ac);
        }
        o.setAddress(a);
    }

    private static String normalizeLanguage(String lang) {
        String l = lang == null ? "" : lang.trim().toLowerCase();
        if (!l.equals("es") && !l.equals("en")) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "defaultLanguage");
        }
        return l;
    }

    private void assertNotClosed(Organization o) {
        if (o.getStatus() == OrganizationStatus.CLOSED) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, o.getStatus());
        }
    }

    /** [V6] Lista de requisitos de activación (se lee de las tablas reales; M03/M04/M05 los irán llenando). */
    OrgResponse.Activation activation(Organization o) {
        return new OrgResponse.Activation(
                contracts.existsActive(o.getId()),
                branches.existsByOrganizationIdAndMainTrueAndStatus(o.getId(), StatusType.ACTIVE),
                accesses.existsByOrganizationIdAndRoleAndStatusIn(o.getId(), RoleType.ORG_ADMIN,
                        List.of(AccessStatus.ACTIVE, AccessStatus.INVITED)));
    }

    // ---------------------------------------------------------------- mapeo

    private OrgResponse toResponse(Organization o) {
        List<OrgResponse.SlugRedirectInfo> reds = redirects
                .findByOrganizationIdAndUntilAtAfterOrderByUntilAtDesc(o.getId(), clock.instant()).stream()
                .map(r -> new OrgResponse.SlugRedirectInfo(r.getOldSlug(), r.getUntilAt())).toList();
        return new OrgResponse(o.getId(), o.getName(), o.getLegalName(), o.getTaxId(), o.getSlug(), o.getCountry(),
                o.getEmail(), o.getPhone(), o.getFoundedDate(), o.getTimezone(), o.getCurrency(), o.getDefaultLanguage(),
                o.getFiscalYearStartMonth(), toDto(o.getAddress()), o.getStatus(), o.getStatusReason(), o.getTrialEndsAt(),
                o.getActivatedAt(), o.getClosedAt(), o.getRetentionUntil(), activation(o), TRANSITIONS.get(o.getStatus()),
                reds, o.getCreatedAt(), o.getUpdatedAt());
    }

    private MyOrganizationResponse toMine(Organization o, boolean admin, boolean canEdit) {
        Contract active = contracts.findByOrganizationIdAndStatus(o.getId(), ContractStatus.ACTIVE).stream().findFirst().orElse(null);
        MyOrganizationResponse.ContractSummary cs = active == null ? null : new MyOrganizationResponse.ContractSummary(
                active.getStatus().name(), active.getStartDate(), active.getEndDate(), active.getMaxLicenses(),
                contracts.findActiveModuleCodes(o.getId()));
        return new MyOrganizationResponse(o.getId(), o.getName(), admin ? o.getLegalName() : null, admin ? o.getTaxId() : null,
                o.getSlug(), o.getCountry(), o.getEmail(), o.getPhone(), o.getFoundedDate(), o.getTimezone(), o.getCurrency(),
                o.getDefaultLanguage(), o.getFiscalYearStartMonth(), toDto(o.getAddress()), o.getStatus(), canEdit, cs);
    }

    private static AddressDto toDto(Address a) {
        return a == null ? new AddressDto(null, null, null, null, null, null)
                : new AddressDto(a.getLine(), a.getDistrict(), a.getCity(), a.getRegion(), a.getCountry(), a.getReference());
    }

    // ---------------------------------------------------------------- utilidades

    private Organization find(UUID id) {
        return organizations.findById(id).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private static UUID requireOrg(AuthenticatedActor actor) {
        if (actor.organizationId() == null) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        return actor.organizationId();
    }

    /** Copia superficial de los campos auditados, para calcular el diff después de editar. */
    private static Organization snapshot(Organization o) {
        Organization c = new Organization();
        c.setName(o.getName());
        c.setLegalName(o.getLegalName());
        c.setTaxId(o.getTaxId());
        c.setCountry(o.getCountry());
        c.setEmail(o.getEmail());
        c.setPhone(o.getPhone());
        c.setFoundedDate(o.getFoundedDate());
        c.setTimezone(o.getTimezone());
        c.setCurrency(o.getCurrency());
        c.setDefaultLanguage(o.getDefaultLanguage());
        c.setFiscalYearStartMonth(o.getFiscalYearStartMonth());
        Address a = o.getAddress() == null ? new Address() : o.getAddress();
        c.setAddress(new Address(a.getLine(), a.getDistrict(), a.getCity(), a.getRegion(), a.getCountry(), a.getReference()));
        return c;
    }

    private static void trackAll(Map<String, Object> ch, Organization b, Organization a) {
        track(ch, "name", b.getName(), a.getName());
        track(ch, "legalName", b.getLegalName(), a.getLegalName());
        track(ch, "taxId", b.getTaxId(), a.getTaxId());
        track(ch, "country", b.getCountry(), a.getCountry());
        track(ch, "email", b.getEmail(), a.getEmail());
        track(ch, "phone", b.getPhone(), a.getPhone());
        track(ch, "foundedDate", b.getFoundedDate(), a.getFoundedDate());
        track(ch, "timezone", b.getTimezone(), a.getTimezone());
        track(ch, "currency", b.getCurrency(), a.getCurrency());
        track(ch, "defaultLanguage", b.getDefaultLanguage(), a.getDefaultLanguage());
        track(ch, "fiscalYearStartMonth", b.getFiscalYearStartMonth(), a.getFiscalYearStartMonth());
        Address x = b.getAddress(), y = a.getAddress();
        track(ch, "address.line", x.getLine(), y.getLine());
        track(ch, "address.district", x.getDistrict(), y.getDistrict());
        track(ch, "address.city", x.getCity(), y.getCity());
        track(ch, "address.region", x.getRegion(), y.getRegion());
        track(ch, "address.country", x.getCountry(), y.getCountry());
        track(ch, "address.reference", x.getReference(), y.getReference());
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

    private static String sortField(String field) {
        return switch (field) {
            case "name", "slug", "status", "country", "taxId", "createdAt", "activatedAt" -> field;
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

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return hasText(s) ? s.trim() : null;
    }
}
