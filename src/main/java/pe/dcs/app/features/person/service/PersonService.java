package pe.dcs.app.features.person.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.auth.service.SecretCipher;
import pe.dcs.app.features.organization.dto.AddressDto;
import pe.dcs.app.features.person.domain.Person;
import pe.dcs.app.features.person.domain.PersonRepository;
import pe.dcs.app.features.person.dto.PersonDtos;
import pe.dcs.app.features.person.dto.PersonDtos.BranchPeriod;
import pe.dcs.app.features.person.dto.PersonDtos.BranchRef;
import pe.dcs.app.features.person.dto.PersonDtos.HouseholdRef;
import pe.dcs.app.features.person.dto.PersonDtos.TagRef;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.vo.Address;
import pe.dcs.app.shared.vo.ContactValidator;
import pe.dcs.app.shared.vo.DocumentType;
import pe.dcs.app.shared.vo.DocumentValidator;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.util.pagination.SortRequest;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * M06 · Personas (parte 1). Ficha única por persona en la organización.
 *
 * <p>Reglas: [V1] documento único por organización · [V2] documento válido por tipo · [V3] nombres 1–80 con letras, espacios y
 * {@code '-.} · [V4] nacimiento entre 1900-01-01 y hoy · [V5] correo y teléfono válidos · [V6] adultos con teléfono o correo ·
 * [V7] cambiar el documento exige motivo · [V16] fuera del alcance de sedes → 404. Las notas reservadas y alergias se guardan
 * cifradas y solo las lee o escribe quien tiene la acción H. Un ORG_BRANCH_ADMIN no recibe H/M/I/P (tope en AuthorizationService).
 */
@Service
@RequiredArgsConstructor
public class PersonService {

    static final String MODULE = "PERSON";
    static final String ENTITY = "Person";

    private static final Pattern NAME = Pattern.compile("^\\p{L}[\\p{L} '\\-.]*$");
    private static final Set<String> SEX = Set.of("MALE", "FEMALE");
    private static final Set<String> MARITAL = Set.of("SINGLE", "MARRIED", "COMMON_LAW", "WIDOWED", "DIVORCED", "SEPARATED");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE", "DECEASED", "MERGED");
    private static final LocalDate MIN_BIRTH = LocalDate.of(1900, 1, 1);
    private static final int MAX_TAGS = 20;
    static final int MAX_EXPORT_ROWS = 50_000;

    private final PersonRepository persons;
    private final PersonLookupService lookup;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authorization;
    private final SecretCipher cipher;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ConsentService consents;

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<PersonDtos.Summary> search(AccessScope scope, PersonDtos.Search req) {
        PersonDtos.Search.Filters f = req == null || req.filters() == null
                ? new PersonDtos.Search.Filters(null, null, null, null, null, null, null, null, null) : req.filters();
        LocalDate today = LocalDate.now(clock);
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId());
        StringBuilder w = where(scope, f, ps, today);

        Long total = jdbc.queryForObject("select count(*) from person p where " + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 10 : Math.max(1, Math.min(req.pagination().getSize(), 100));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);

        List<PersonDtos.Summary> rows = summaryRows(w, ps, orderBy(req == null ? null : req.sorts()), today, true);
        rows = attachTags(rows);
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    /** Filas de la lista (con o sin paginación; {@code :lim} y {@code :off} deben estar en {@code ps} si {@code paged}). */
    private List<PersonDtos.Summary> summaryRows(StringBuilder w, MapSqlParameterSource ps, String order, LocalDate today, boolean paged) {
        return jdbc.query("""
                select p.id, p.first_name, p.last_name, p.doc_type, p.doc_number, p.birth_date, p.sex, p.phone, p.email, p.status,
                       p.primary_branch_id, b.name as branch_name,
                       (select h.name from household_member m join household h on h.id = m.household_id
                         where m.person_id = p.id and m.left_at is null) as household_name
                  from person p left join branch b on b.id = p.primary_branch_id
                 where """ + " " + w + " order by " + order + (paged ? " limit :lim offset :off" : ""), ps, (rs, i) -> {
            java.sql.Date bd = rs.getDate("birth_date");
            LocalDate birth = bd == null ? null : bd.toLocalDate();
            return new PersonDtos.Summary((UUID) rs.getObject("id"),
                    (rs.getString("last_name") + ", " + rs.getString("first_name")).trim(), rs.getString("first_name"), rs.getString("last_name"),
                    rs.getString("doc_type"), rs.getString("doc_number"), PersonScope.age(birth, today),
                    birth == null ? null : PersonScope.minor(birth, today), rs.getString("sex"), rs.getString("phone"), rs.getString("email"),
                    rs.getString("status"), (UUID) rs.getObject("primary_branch_id"), rs.getString("branch_name"),
                    rs.getString("household_name"), List.of());
        });
    }

    public record ExportResult(byte[] content, int rows) {
    }

    /** Exporta la lista a XLSX (acción X) con los mismos filtros y alcance de la búsqueda; nunca incluye datos reservados [T-C12]. */
    @Transactional
    public ExportResult exportList(AccessScope scope, PersonDtos.Search req) {
        PersonDtos.Search.Filters f = req == null || req.filters() == null
                ? new PersonDtos.Search.Filters(null, null, null, null, null, null, null, null, null) : req.filters();
        LocalDate today = LocalDate.now(clock);
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId());
        StringBuilder w = where(scope, f, ps, today);
        Long total = jdbc.queryForObject("select count(*) from person p where " + w, ps, Long.class);
        if (total != null && total > MAX_EXPORT_ROWS) {
            throw new Exceptions("error.person.exportTooLarge", HttpStatus.UNPROCESSABLE_ENTITY, MAX_EXPORT_ROWS);
        }
        List<PersonDtos.Summary> rows = attachTags(summaryRows(w, ps, orderBy(req == null ? null : req.sorts()), today, false));
        List<String> headers = List.of("Tipo de documento", "Número de documento", "Apellidos", "Nombres", "Sexo", "Edad", "Teléfono", "Correo",
                "Sede", "Hogar", "Etiquetas", "Estado");
        List<List<Object>> data = new java.util.ArrayList<>(rows.size());
        for (PersonDtos.Summary r : rows) {
            data.add(java.util.Arrays.asList(r.docType(), r.docNumber(), r.lastName(), r.firstName(), r.sex(), r.age(), r.phone(), r.email(),
                    r.branchName(), r.householdName(), r.tags().stream().map(PersonDtos.TagRef::name).collect(java.util.stream.Collectors.joining(", ")), r.status()));
        }
        byte[] bytes = pe.dcs.app.shared.export.XlsxWriter.write("Personas", headers, data);
        audit.record(new AuditService.Command(MODULE, "EXPORT", ENTITY, null, scope.organizationId(), null,
                diff("rows", rows.size(), "branchId", f.branchId(), "status", f.status(), "tagId", f.tagId())));
        return new ExportResult(bytes, rows.size());
    }

    /** Condiciones comunes de la lista y de la exportación: organización, alcance y filtros. */
    private StringBuilder where(AccessScope scope, PersonDtos.Search.Filters f, MapSqlParameterSource ps, LocalDate today) {
        StringBuilder w = new StringBuilder("p.organization_id = :org and ").append(PersonScope.visibleRead(scope, ps, "p"));

        if (hasText(f.status())) {
            String st = f.status().trim().toUpperCase();
            if (!STATUSES.contains(st)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
            }
            w.append(" and p.status = :status");
            ps.addValue("status", st);
        } else {
            w.append(" and p.status <> 'MERGED'");
        }
        if (!Boolean.TRUE.equals(f.anonymized())) {
            w.append(" and p.anonymized_at is null");                                     // las anonimizadas solo se ven si se piden
        }
        if (hasText(f.q())) {
            String[] words = f.q().trim().toLowerCase().split("\\s+");
            for (int i = 0; i < Math.min(words.length, 5); i++) {
                String k = "w" + i;
                ps.addValue(k, "%" + words[i].replace("%", "").replace("_", "") + "%");
                w.append(" and (lower(p.first_name || ' ' || p.last_name) like :").append(k)
                        .append(" or lower(p.doc_number) like :").append(k)
                        .append(" or lower(coalesce(p.email, '')) like :").append(k)
                        .append(" or coalesce(p.phone, '') like :").append(k).append(")");
            }
        }
        if (f.branchId() != null) {
            w.append(" and p.primary_branch_id = :branch");
            ps.addValue("branch", f.branchId());
        }
        if (f.tagId() != null) {
            w.append(" and exists (select 1 from person_tag pt where pt.person_id = p.id and pt.tag_id = :tag)");
            ps.addValue("tag", f.tagId());
        }
        if (f.ageFrom() != null) {
            w.append(" and p.birth_date <= :maxBirth");
            ps.addValue("maxBirth", java.sql.Date.valueOf(today.minusYears(Math.max(0, f.ageFrom()))));
        }
        if (f.ageTo() != null) {
            w.append(" and p.birth_date > :minBirth");
            ps.addValue("minBirth", java.sql.Date.valueOf(today.minusYears(Math.max(0, f.ageTo()) + 1L)));
        }
        if (Boolean.TRUE.equals(f.minor())) {
            w.append(" and p.birth_date > :adultBirth");
            ps.addValue("adultBirth", java.sql.Date.valueOf(today.minusYears(PersonScope.ADULT_AGE)));
        }
        if ("WITH".equalsIgnoreCase(f.household())) {
            w.append(" and exists (select 1 from household_member m where m.person_id = p.id and m.left_at is null)");
        } else if ("WITHOUT".equalsIgnoreCase(f.household())) {
            w.append(" and not exists (select 1 from household_member m where m.person_id = p.id and m.left_at is null)");
        }

        return w;
    }

    @Transactional(readOnly = true)
    public PersonDtos.Response get(AuthenticatedActor actor, AccessScope scope, UUID id) {
        Person p = loadReadable(scope, id);
        return toResponse(actor, p, !isVisible(scope, p.getId()));
    }

    /** Busca por documento antes de crear. Si existe fuera del alcance: solo se avisa que existe en otra sede, sin datos [V16]. */
    @Transactional(readOnly = true)
    public PersonDtos.Lookup lookupByDocument(AccessScope scope, DocumentType type, String docNumber) {
        String doc = DocumentValidator.normalize(docNumber);
        if (type == null || type == DocumentType.RUC || !DocumentValidator.isValid(type, doc)) {
            throw new Exceptions("error.common.docInvalid", HttpStatus.BAD_REQUEST, doc == null ? "" : doc, type);
        }
        PersonLookupService.PersonMin found = lookup.find(scope.organizationId(), type, doc).orElse(null);
        if (found == null) {
            return new PersonDtos.Lookup(false, false, false, null, null, null, null);
        }
        boolean visible = isVisible(scope, found.id());
        if (!visible) {
            return new PersonDtos.Lookup(true, false, true, null, null, null, null);
        }
        return new PersonDtos.Lookup(true, true, false, found.id(), found.fullName(), found.status(), found.branchName());
    }

    /** Selector de personas (PersonPicker): personas activas del alcance. */
    @Transactional(readOnly = true)
    public List<PersonDtos.Picker> picker(AccessScope scope, String q, int limit) {
        LocalDate today = LocalDate.now(clock);
        return lookup.search(scope, q, limit).stream()
                .map(p -> new PersonDtos.Picker(p.id(), p.fullName(), p.docType() == null ? null : p.docType().name(), p.docNumber(), p.status(),
                        PersonScope.age(p.birthDate(), today), p.branchName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PersonDtos.History history(AccessScope scope, UUID id) {
        Person p = loadReadable(scope, id);
        List<BranchPeriod> periods = jdbc.query("""
                select pb.branch_id, b.name, pb.from_date, pb.to_date, pb.is_current, pb.reason
                  from person_branch pb join branch b on b.id = pb.branch_id
                 where pb.person_id = :id order by pb.from_date desc, pb.created_at desc""", new MapSqlParameterSource("id", p.getId()),
                (rs, i) -> new BranchPeriod((UUID) rs.getObject(1), rs.getString(2), rs.getDate(3).toLocalDate(),
                        rs.getDate(4) == null ? null : rs.getDate(4).toLocalDate(), rs.getBoolean(5), rs.getString(6)));
        List<PersonDtos.TimelineItem> timeline = jdbc.query("""
                select a.at, a.action, a.actor_role, a.diff::text as diff,
                       coalesce(pp.first_name || ' ' || pp.last_name, ps.first_name || ' ' || ps.last_name) as actor
                  from audit_event a
                  left join person pp on pp.id = a.actor_id and a.actor_type = 'PERSON'
                  left join platform_staff ps on ps.id = a.actor_id and a.actor_type = 'STAFF'
                 where a.entity_type = 'Person' and a.entity_id = :id and a.organization_id = :org
                 order by a.at desc, a.id desc limit 100""", new MapSqlParameterSource("id", p.getId().toString()).addValue("org", scope.organizationId()),
                (rs, i) -> {
                    List<String> fields = new ArrayList<>();
                    String detail = null;
                    try {
                        String raw = rs.getString("diff");
                        JsonNode d = raw == null ? null : mapper.readTree(raw);
                        if (d != null) {
                            if (d.has("fields")) {
                                d.get("fields").forEach(n -> fields.add(n.asText()));
                            }
                            List<String> parts = new ArrayList<>();
                            for (String k : List.of("from", "to", "reason")) {
                                if (d.hasNonNull(k)) {
                                    parts.add(d.get(k).asText());
                                }
                            }
                            detail = parts.isEmpty() ? null : String.join(" · ", parts);
                        }
                    } catch (Exception ignored) {
                        // un diff ilegible no debe romper la línea de tiempo
                    }
                    return new PersonDtos.TimelineItem(rs.getTimestamp("at").toInstant(), rs.getString("action"), rs.getString("actor"),
                            rs.getString("actor_role"), fields, detail);
                });
        return new PersonDtos.History(periods, timeline);
    }

    // ---------------------------------------------------------------- alta y edición

    @Transactional
    public PersonDtos.Response create(AuthenticatedActor actor, AccessScope scope, PersonDtos.Request r) {
        LocalDate today = LocalDate.now(clock);
        Validated v = validate(r, today, false);
        if (r.primaryBranchId() == null) {
            throw new Exceptions("error.person.branchRequired", HttpStatus.BAD_REQUEST);
        }
        BranchRef branch = activeBranch(scope, r.primaryBranchId());
        assertDocumentFree(scope, v.docType, v.docNumber, null);

        Person p = new Person();
        p.setOrganizationId(scope.organizationId());
        p.setDocType(v.docType);
        p.setDocNumber(v.docNumber);
        apply(p, v, r);
        p.setPrimaryBranchId(branch.id());
        p.setStatus("ACTIVE");
        if (hasSensitive(actor)) {
            p.setPrivateNotes(encrypt(r.privateNotes()));
            p.setAllergies(encrypt(r.allergies()));
        } else if (hasText(r.privateNotes()) || hasText(r.allergies())) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        try {
            persons.saveAndFlush(p);
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.person.docTaken", HttpStatus.CONFLICT);
        }
        openBranchPeriod(actor.ownerId(), p.getId(), branch.id(), today, null);
        if (Boolean.TRUE.equals(r.consentGranted())) {
            consents.grantDefaults(p.getOrganizationId(), p.getId(), "STAFF", scope.personId());
        }
        audit.record(new AuditService.Command(MODULE, "CREATE", ENTITY, p.getId(), p.getOrganizationId(), branch.id(),
                diff("branch", branch.name(), "consent", Boolean.TRUE.equals(r.consentGranted()))));
        return toResponse(actor, p);
    }

    @Transactional
    public PersonDtos.Response update(AuthenticatedActor actor, AccessScope scope, UUID id, PersonDtos.Request r) {
        Person p = loadVisible(scope, id);
        assertNotMerged(p);
        checkVersion(p, r.version());
        LocalDate today = LocalDate.now(clock);
        Validated v = validate(r, today, p.getDocType() == null);

        List<String> changed = new ArrayList<>();
        boolean docChanged = v.docType != p.getDocType() || !java.util.Objects.equals(v.docNumber, p.getDocNumber());
        boolean firstDoc = docChanged && p.getDocType() == null;          // completar el documento de quien no tenía no exige motivo
        if (docChanged) {
            if (!firstDoc && !hasText(r.documentChangeReason())) {
                throw new Exceptions("error.person.docChangeReason", HttpStatus.BAD_REQUEST);                    // [V7]
            }
            assertDocumentFree(scope, v.docType, v.docNumber, p.getId());
            p.setDocType(v.docType);
            p.setDocNumber(v.docNumber);
            changed.add("document");
        }
        trackChanges(changed, p, v, r);
        apply(p, v, r);

        if (hasSensitive(actor)) {
            if (differs(decrypt(p.getPrivateNotes()), blankToNull(r.privateNotes()))) {
                p.setPrivateNotes(encrypt(r.privateNotes()));
                changed.add("privateNotes");
            }
            if (differs(decrypt(p.getAllergies()), blankToNull(r.allergies()))) {
                p.setAllergies(encrypt(r.allergies()));
                changed.add("allergies");
            }
        } else if (hasText(r.privateNotes()) || hasText(r.allergies())) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        if (p.getPrimaryBranchId() == null && r.primaryBranchId() != null) {                                      // personas de F0 sin sede
            BranchRef b = activeBranch(scope, r.primaryBranchId());
            p.setPrimaryBranchId(b.id());
            openBranchPeriod(actor.ownerId(), p.getId(), b.id(), today, null);
            changed.add("primaryBranch");
        }
        try {
            persons.saveAndFlush(p);
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.person.docTaken", HttpStatus.CONFLICT);
        }
        if (!changed.isEmpty()) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("fields", changed);
            if (docChanged) {
                if (hasText(r.documentChangeReason())) {
                    d.put("reason", r.documentChangeReason().trim());
                }
            }
            audit.record(new AuditService.Command(MODULE, docChanged ? "DOC_CHANGE" : "UPDATE", ENTITY, p.getId(), p.getOrganizationId(),
                    p.getPrimaryBranchId(), d));
        }
        return toResponse(actor, p);
    }

    @Transactional
    public PersonDtos.Response changeStatus(AuthenticatedActor actor, AccessScope scope, UUID id, PersonDtos.StatusRequest r) {
        Person p = loadVisible(scope, id);
        assertNotMerged(p);
        checkVersion(p, r.version());
        String to = r.status() == null ? "" : r.status().trim().toUpperCase();
        if (!Set.of("ACTIVE", "INACTIVE", "DECEASED").contains(to)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
        }
        String from = p.getStatus();
        if (from.equals(to)) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, from);
        }
        LocalDate today = LocalDate.now(clock);
        if (!to.equals("ACTIVE") && hasActiveAccess(p.getId())) {
            throw new Exceptions("error.person.hasActiveAccess", HttpStatus.CONFLICT);
        }
        String reason = blankToNull(r.reason());
        if ((to.equals("INACTIVE") || (to.equals("ACTIVE") && from.equals("DECEASED"))) && reason == null) {
            throw new Exceptions("error.common.reasonRequired", HttpStatus.BAD_REQUEST);
        }
        LocalDate deceasedAt = null;
        if (to.equals("DECEASED")) {
            if (r.date() == null) {
                throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "fecha de fallecimiento");
            }
            if (r.date().isAfter(today)) {
                throw new Exceptions("error.common.futureDate", HttpStatus.BAD_REQUEST);
            }
            if (p.getBirthDate() != null && r.date().isBefore(p.getBirthDate())) {
                throw new Exceptions("error.person.deathBeforeBirth", HttpStatus.BAD_REQUEST);
            }
            deceasedAt = r.date();
        }
        p.setStatus(to);
        p.setStatusReason(to.equals("ACTIVE") ? null : reason);
        p.setDeceasedAt(deceasedAt);
        persons.saveAndFlush(p);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("from", from);
        d.put("to", to);
        if (reason != null) {
            d.put("reason", reason);
        }
        audit.record(new AuditService.Command(MODULE, "STATUS", ENTITY, p.getId(), p.getOrganizationId(), p.getPrimaryBranchId(), d));
        return toResponse(actor, p);
    }

    /** Traslada la sede principal conservando el historial en person_branch. Quien traslada debe ver la sede destino. */
    @Transactional
    public PersonDtos.Response transfer(AuthenticatedActor actor, AccessScope scope, UUID id, PersonDtos.BranchRequest r) {
        Person p = loadVisible(scope, id);
        assertNotMerged(p);
        checkVersion(p, r.version());
        if (r.branchId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
        }
        BranchRef dest = activeBranch(scope, r.branchId());
        if (dest.id().equals(p.getPrimaryBranchId())) {
            return toResponse(actor, p);
        }
        LocalDate today = LocalDate.now(clock);
        String fromName = p.getPrimaryBranchId() == null ? null : branchRef(scope.organizationId(), p.getPrimaryBranchId()).name();
        jdbc.update("update person_branch set to_date = :d, is_current = false where person_id = :p and is_current",
                new MapSqlParameterSource("d", java.sql.Date.valueOf(today)).addValue("p", p.getId()));
        openBranchPeriod(actor.ownerId(), p.getId(), dest.id(), today, blankToNull(r.reason()));
        p.setPrimaryBranchId(dest.id());
        persons.saveAndFlush(p);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("from", fromName);
        d.put("to", dest.name());
        if (hasText(r.reason())) {
            d.put("reason", r.reason().trim());
        }
        audit.record(new AuditService.Command(MODULE, "TRANSFER", ENTITY, p.getId(), p.getOrganizationId(), dest.id(), d));
        return toResponse(actor, p);
    }

    /**
     * Aplica un traslado ya autorizado (M21): cierra el periodo de sede vigente y abre el del destino. No comprueba el alcance de quien
     * ejecuta (el módulo de traslados ya lo hizo) y corre dentro de la transacción del traslado.
     */
    @Transactional
    public void applyTransfer(UUID actorPersonId, UUID orgId, UUID personId, UUID toBranchId, String reason) {
        Person p = persons.findByIdAndOrganizationId(personId, orgId).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        assertNotMerged(p);
        if (!"ACTIVE".equals(p.getStatus())) {
            throw new Exceptions("error.transfer.personInactive", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        LocalDate today = LocalDate.now(clock);
        String fromName = p.getPrimaryBranchId() == null ? null : branchRef(orgId, p.getPrimaryBranchId()).name();
        String toName = branchRef(orgId, toBranchId).name();
        jdbc.update("update person_branch set to_date = :d, is_current = false where person_id = :p and is_current",
                new MapSqlParameterSource("d", java.sql.Date.valueOf(today)).addValue("p", p.getId()));
        openBranchPeriod(actorPersonId, p.getId(), toBranchId, today, blankToNull(reason));
        p.setPrimaryBranchId(toBranchId);
        persons.saveAndFlush(p);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("from", fromName);
        d.put("to", toName);
        d.put("via", "BRANCH_TRANSFER");
        if (hasText(reason)) {
            d.put("reason", reason.trim());
        }
        audit.record(new AuditService.Command(MODULE, "TRANSFER", ENTITY, p.getId(), orgId, toBranchId, d));
    }

    /** Reemplaza el conjunto de etiquetas de la persona. */
    @Transactional
    public PersonDtos.Response setTags(AuthenticatedActor actor, AccessScope scope, UUID id, PersonDtos.TagsRequest r) {
        Person p = loadVisible(scope, id);
        assertNotMerged(p);
        Set<UUID> wanted = new LinkedHashSet<>(r == null || r.tagIds() == null ? List.of() : r.tagIds());
        if (wanted.size() > MAX_TAGS) {
            throw new Exceptions("error.person.tooManyTags", HttpStatus.UNPROCESSABLE_ENTITY, MAX_TAGS);
        }
        if (!wanted.isEmpty()) {
            Long ok = jdbc.queryForObject("select count(*) from tag where organization_id = :org and id in (:ids)",
                    new MapSqlParameterSource("org", scope.organizationId()).addValue("ids", wanted), Long.class);
            if (ok == null || ok != wanted.size()) {
                throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
            }
        }
        jdbc.update("delete from person_tag where person_id = :p", new MapSqlParameterSource("p", p.getId()));
        Timestamp now = Timestamp.from(clock.instant());
        for (UUID t : wanted) {
            jdbc.update("insert into person_tag (person_id, tag_id, created_at, created_by) values (:p, :t, :at, :by)",
                    new MapSqlParameterSource("p", p.getId()).addValue("t", t).addValue("at", now).addValue("by", actor.ownerId()));
        }
        audit.record(new AuditService.Command(MODULE, "TAGS", ENTITY, p.getId(), p.getOrganizationId(), p.getPrimaryBranchId(),
                diff("count", wanted.size())));
        return toResponse(actor, p);
    }

    // ---------------------------------------------------------------- alta mínima para otros módulos

    /**
     * Alta mínima de una persona para otros módulos (visitantes): nombre, contacto y, si se tiene, documento. No exige los permisos
     * de Personas: el módulo que llama ya comprobó los suyos y el alcance de la sede. La persona queda ACTIVE en esa sede.
     */
    @Transactional
    public UUID registerBasic(UUID orgId, UUID branchId, String firstName, String lastName, String phoneRaw, String emailRaw,
                              DocumentType docType, String docNumber, UUID byPerson, String via) {
        String first = name(firstName, "nombres");
        String last = name(lastName, "apellidos");
        String phone = phone(phoneRaw);
        String email = blankToNull(emailRaw) == null ? null : ContactValidator.normalizeEmail(emailRaw);
        if (email != null && !ContactValidator.isValidEmail(email)) {
            throw new Exceptions("error.common.emailInvalid", HttpStatus.BAD_REQUEST);
        }
        if (phone == null && email == null) {
            throw new Exceptions("error.person.contactRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String doc = DocumentValidator.normalize(docNumber);
        DocumentType type = null;
        if (doc != null && !doc.isBlank()) {
            type = docType;
            if (type == null || type == DocumentType.RUC || !DocumentValidator.isValid(type, doc)) {
                throw new Exceptions("error.common.docInvalid", HttpStatus.BAD_REQUEST, doc, type);
            }
            if (lookup.find(orgId, type, doc).isPresent()) {
                throw new Exceptions("error.person.docTaken", HttpStatus.CONFLICT);
            }
        } else {
            doc = null;
        }
        Person p = new Person();
        p.setOrganizationId(orgId);
        p.setDocType(type);
        p.setDocNumber(doc);
        p.setFirstName(first);
        p.setLastName(last);
        p.setPhone(phone);
        p.setEmail(email);
        p.setPrimaryBranchId(branchId);
        p.setStatus("ACTIVE");
        try {
            persons.saveAndFlush(p);
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.person.docTaken", HttpStatus.CONFLICT);
        }
        LocalDate today = LocalDate.now(clock);
        openBranchPeriod(byPerson, p.getId(), branchId, today, null);
        audit.record(new AuditService.Command(MODULE, "CREATE", ENTITY, p.getId(), orgId, branchId, diff("via", via)));
        return p.getId();
    }

    /**
     * Valida una fila de importación con las mismas reglas del alta (documento, nombres, fechas, contacto) sin escribir nada.
     * Lanza {@link Exceptions} con el mensaje de la primera regla que falla.
     */
    public void validateForImport(PersonDtos.Request r) {
        validate(r, LocalDate.now(clock), false);
    }

    /** Teléfono normalizado como se guarda en la ficha (9 dígitos peruanos → +51); null si viene vacío. */
    public static String normalizePhone(String raw) {
        return phone(raw);
    }

    // ---------------------------------------------------------------- validación y mapeo

    /** Valores ya normalizados de una solicitud. */
    private static final class Validated {
        DocumentType docType;
        String docNumber;
        String firstName;
        String lastName;
        String sex;
        String marital;
        String email;
        String phone;
        String whatsapp;
    }

    private Validated validate(PersonDtos.Request r, LocalDate today, boolean allowNoDoc) {
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        Validated v = new Validated();
        v.docType = r.docType();
        v.docNumber = DocumentValidator.normalize(r.docNumber());
        boolean noDoc = v.docNumber == null || v.docNumber.isBlank();
        if (noDoc && allowNoDoc) {                                       // persona registrada sin documento (visitante): se completa después
            v.docType = null;
            v.docNumber = null;
        } else {
            if (v.docType == null || noDoc) {
                throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "documento");
            }
            if (v.docType == DocumentType.RUC || !DocumentValidator.isValid(v.docType, v.docNumber)) {                // [V2]
                throw new Exceptions("error.common.docInvalid", HttpStatus.BAD_REQUEST, v.docNumber, v.docType);
            }
        }
        v.firstName = name(r.firstName(), "nombres");                                                              // [V3]
        v.lastName = name(r.lastName(), "apellidos");
        v.sex = enumOrNull(r.sex(), SEX, "sexo");
        v.marital = enumOrNull(r.maritalStatus(), MARITAL, "estado civil");
        if (r.birthDate() != null && (r.birthDate().isBefore(MIN_BIRTH) || r.birthDate().isAfter(today))) {       // [V4]
            throw new Exceptions("error.person.birthInvalid", HttpStatus.BAD_REQUEST);
        }
        if (r.joinedAt() != null && r.joinedAt().isAfter(today)) {
            throw new Exceptions("error.common.futureDate", HttpStatus.BAD_REQUEST);
        }
        v.email = blankToNull(r.email()) == null ? null : ContactValidator.normalizeEmail(r.email());
        if (v.email != null && !ContactValidator.isValidEmail(v.email)) {                                          // [V5]
            throw new Exceptions("error.common.emailInvalid", HttpStatus.BAD_REQUEST);
        }
        v.phone = phone(r.phone());
        v.whatsapp = phone(r.whatsapp());
        if (!PersonScope.minor(r.birthDate(), today) && v.phone == null && v.email == null) {                     // [V6]
            throw new Exceptions("error.person.contactRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return v;
    }

    private static String name(String raw, String label) {
        String n = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (n.isEmpty()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, label);
        }
        if (n.length() > 80 || !NAME.matcher(n).matches()) {
            throw new Exceptions("error.person.nameInvalid", HttpStatus.BAD_REQUEST);
        }
        return n;
    }

    private static String enumOrNull(String raw, Set<String> allowed, String label) {
        if (!hasText(raw)) {
            return null;
        }
        String v = raw.trim().toUpperCase();
        if (!allowed.contains(v)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, label);
        }
        return v;
    }

    /** Quita espacios, guiones y paréntesis; un celular peruano de 9 dígitos (9XXXXXXXX) se guarda con +51. */
    private static String phone(String raw) {
        if (!hasText(raw)) {
            return null;
        }
        String p = raw.replaceAll("[\\s\\-().]", "");
        if (p.matches("^9\\d{8}$")) {
            p = "+51" + p;
        }
        if (!ContactValidator.isValidPhone(p)) {
            throw new Exceptions("error.common.phoneInvalid", HttpStatus.BAD_REQUEST);
        }
        return p;
    }

    private void apply(Person p, Validated v, PersonDtos.Request r) {
        p.setFirstName(v.firstName);
        p.setLastName(v.lastName);
        p.setSex(v.sex);
        p.setBirthDate(r.birthDate());
        p.setMaritalStatus(v.marital);
        p.setEmail(v.email);
        p.setPhone(v.phone);
        p.setWhatsapp(v.whatsapp);
        p.setAddress(toAddress(r.address()));
        p.setOccupation(blankToNull(r.occupation()));
        p.setJoinedAt(r.joinedAt());
    }

    private static void trackChanges(List<String> changed, Person p, Validated v, PersonDtos.Request r) {
        cmp(changed, "firstName", p.getFirstName(), v.firstName);
        cmp(changed, "lastName", p.getLastName(), v.lastName);
        cmp(changed, "sex", p.getSex(), v.sex);
        cmp(changed, "birthDate", p.getBirthDate(), r.birthDate());
        cmp(changed, "maritalStatus", p.getMaritalStatus(), v.marital);
        cmp(changed, "email", p.getEmail(), v.email);
        cmp(changed, "phone", p.getPhone(), v.phone);
        cmp(changed, "whatsapp", p.getWhatsapp(), v.whatsapp);
        cmp(changed, "occupation", p.getOccupation(), blankToNull(r.occupation()));
        cmp(changed, "joinedAt", p.getJoinedAt(), r.joinedAt());
        Address a = toAddress(r.address());
        Address o = p.getAddress() == null ? new Address() : p.getAddress();
        if (differs(o.getLine(), a.getLine()) || differs(o.getDistrict(), a.getDistrict()) || differs(o.getCity(), a.getCity())
                || differs(o.getRegion(), a.getRegion()) || differs(o.getCountry(), a.getCountry()) || differs(o.getReference(), a.getReference())) {
            changed.add("address");
        }
    }

    private static void cmp(List<String> changed, String field, Object before, Object after) {
        if (!java.util.Objects.equals(before, after)) {
            changed.add(field);
        }
    }

    private static boolean differs(String a, String b) {
        return !java.util.Objects.equals(blankToNull(a), blankToNull(b));
    }

    private static Address toAddress(AddressDto d) {
        Address a = new Address();
        if (d != null) {
            a.setLine(blankToNull(d.line()));
            a.setDistrict(blankToNull(d.district()));
            a.setCity(blankToNull(d.city()));
            a.setRegion(blankToNull(d.region()));
            a.setCountry(blankToNull(d.country()) == null ? null : d.country().trim().toUpperCase());
            a.setReference(blankToNull(d.reference()));
        }
        return a;
    }

    private static AddressDto toDto(Address a) {
        return a == null ? null : new AddressDto(a.getLine(), a.getDistrict(), a.getCity(), a.getRegion(), a.getCountry(), a.getReference());
    }

    private PersonDtos.Response toResponse(AuthenticatedActor actor, Person p) {
        return toResponse(actor, p, false);
    }

    /** readOnly = la persona se ve solo por una regla de visibilidad entre sedes (M21): sin notas reservadas, foto ni edición. */
    private PersonDtos.Response toResponse(AuthenticatedActor actor, Person p, boolean readOnly) {
        LocalDate today = LocalDate.now(clock);
        boolean sensitive = hasSensitive(actor) && !readOnly;
        String notes = decrypt(p.getPrivateNotes());
        String allergies = decrypt(p.getAllergies());
        BranchRef branch = p.getPrimaryBranchId() == null ? null : branchRef(p.getOrganizationId(), p.getPrimaryBranchId());
        return new PersonDtos.Response(p.getId(), p.getDocType() == null ? null : p.getDocType().name(), p.getDocNumber(), p.getFirstName(), p.getLastName(), p.fullName(),
                p.getSex(), p.getBirthDate(), PersonScope.age(p.getBirthDate(), today), PersonScope.minor(p.getBirthDate(), today),
                p.getMaritalStatus(), p.getEmail(), p.getPhone(), p.getWhatsapp(), toDto(p.getAddress()), p.getOccupation(), branch,
                p.getJoinedAt(), p.getStatus(), p.getStatusReason(), p.getDeceasedAt(), p.getMergedInto(),
                sensitive ? notes : null, sensitive ? allergies : null, !sensitive, notes != null || allergies != null, hasActiveAccess(p.getId()),
                tagsOf(p.getId()), householdOf(p.getId()), p.getVersion(), p.getCreatedAt(), p.getUpdatedAt(), !readOnly && p.getPhotoKey() != null, p.getPhotoUpdatedAt(),
                p.getAnonymizedAt() != null, readOnly);
    }

    // ---------------------------------------------------------------- utilidades

    private Person loadVisible(AccessScope scope, UUID id) {
        Person p = persons.findByIdAndOrganizationId(id, scope.organizationId())
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        if (!isVisible(scope, p.getId())) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);                                    // [V16]
        }
        return p;
    }

    /** Como {@link #loadVisible} pero con la visibilidad de lectura (reglas entre sedes, M21). */
    private Person loadReadable(AccessScope scope, UUID id) {
        Person p = persons.findByIdAndOrganizationId(id, scope.organizationId())
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id).addValue("org", scope.organizationId());
        String vis = PersonScope.visibleRead(scope, ps, "p");
        Long n = jdbc.queryForObject("select count(*) from person p where p.id = :id and p.organization_id = :org and " + vis, ps, Long.class);
        if (n == null || n == 0) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return p;
    }

    private boolean isVisible(AccessScope scope, UUID personId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", personId).addValue("org", scope.organizationId());
        String vis = PersonScope.visible(scope, ps, "p");
        Long n = jdbc.queryForObject("select count(*) from person p where p.id = :id and p.organization_id = :org and " + vis, ps, Long.class);
        return n != null && n > 0;
    }

    private void assertDocumentFree(AccessScope scope, DocumentType type, String doc, UUID exceptId) {
        if (type == null || doc == null) {
            return;
        }
        PersonLookupService.PersonMin other = lookup.find(scope.organizationId(), type, doc).orElse(null);
        if (other == null || other.id().equals(exceptId)) {
            return;
        }
        throw new Exceptions(isVisible(scope, other.id()) ? "error.person.docTaken" : "error.person.existsOtherBranch", HttpStatus.CONFLICT);
    }

    private static void assertNotMerged(Person p) {
        if ("MERGED".equals(p.getStatus())) {
            throw new Exceptions("error.person.merged", HttpStatus.CONFLICT);
        }
        if (p.getAnonymizedAt() != null) {
            throw new Exceptions("error.person.anonymized", HttpStatus.CONFLICT);
        }
    }

    private static void checkVersion(Person p, Long version) {
        if (version != null && !version.equals(p.getVersion())) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
    }

    private boolean hasSensitive(AuthenticatedActor actor) {
        return authorization.effectiveActions(actor, MODULE).contains("H");
    }

    private boolean hasActiveAccess(UUID personId) {
        Boolean b = jdbc.queryForObject("select exists(select 1 from user_access where person_id = :p and status in ('ACTIVE','INVITED'))",
                new MapSqlParameterSource("p", personId), Boolean.class);
        return Boolean.TRUE.equals(b);
    }

    private BranchRef activeBranch(AccessScope scope, UUID branchId) {
        List<Object[]> rows = jdbc.query("select id, name, code, status from branch where id = :id and organization_id = :org",
                new MapSqlParameterSource("id", branchId).addValue("org", scope.organizationId()),
                (rs, i) -> new Object[]{rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4)});
        if (rows.isEmpty() || !scope.canSeeBranch(branchId)) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Object[] b = rows.get(0);
        if (!"ACTIVE".equals(b[3])) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, b[3]);
        }
        return new BranchRef((UUID) b[0], (String) b[1], (String) b[2]);
    }

    private BranchRef branchRef(UUID orgId, UUID branchId) {
        List<BranchRef> rows = jdbc.query("select id, name, code from branch where id = :id and organization_id = :org",
                new MapSqlParameterSource("id", branchId).addValue("org", orgId),
                (rs, i) -> new BranchRef((UUID) rs.getObject(1), rs.getString(2), rs.getString(3)));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void openBranchPeriod(UUID by, UUID personId, UUID branchId, LocalDate from, String reason) {
        jdbc.update("""
                insert into person_branch (id, person_id, branch_id, from_date, is_current, reason, created_at, created_by)
                values (:id, :p, :b, :from, true, :reason, :at, :by)""",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("p", personId).addValue("b", branchId)
                        .addValue("from", java.sql.Date.valueOf(from)).addValue("reason", reason)
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", by));
    }

    private List<TagRef> tagsOf(UUID personId) {
        return jdbc.query("select t.id, t.name, t.color from person_tag pt join tag t on t.id = pt.tag_id where pt.person_id = :p order by lower(t.name)",
                new MapSqlParameterSource("p", personId), (rs, i) -> new TagRef((UUID) rs.getObject(1), rs.getString(2), rs.getString(3)));
    }

    private List<PersonDtos.Summary> attachTags(List<PersonDtos.Summary> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        Map<UUID, List<TagRef>> byPerson = new LinkedHashMap<>();
        List<UUID> ids = rows.stream().map(PersonDtos.Summary::id).toList();
        for (int from = 0; from < ids.size(); from += 1000) {                                                       // el driver limita los parámetros
            jdbc.query("select pt.person_id, t.id, t.name, t.color from person_tag pt join tag t on t.id = pt.tag_id where pt.person_id in (:ids) order by lower(t.name)",
                    new MapSqlParameterSource("ids", ids.subList(from, Math.min(ids.size(), from + 1000))), rs -> {
                        byPerson.computeIfAbsent((UUID) rs.getObject(1), k -> new ArrayList<>())
                                .add(new TagRef((UUID) rs.getObject(2), rs.getString(3), rs.getString(4)));
                    });
        }
        return rows.stream().map(s -> new PersonDtos.Summary(s.id(), s.fullName(), s.firstName(), s.lastName(), s.docType(), s.docNumber(),
                s.age(), s.minor(), s.sex(), s.phone(), s.email(), s.status(), s.branchId(), s.branchName(), s.householdName(),
                byPerson.getOrDefault(s.id(), List.of()))).toList();
    }

    private HouseholdRef householdOf(UUID personId) {
        List<HouseholdRef> rows = jdbc.query("""
                select h.id, h.name, m.role, m.guardian from household_member m join household h on h.id = m.household_id
                 where m.person_id = :p and m.left_at is null""", new MapSqlParameterSource("p", personId),
                (rs, i) -> new HouseholdRef((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String orderBy(List<SortRequest> sorts) {
        String key = sorts == null || sorts.isEmpty() ? "name" : String.valueOf(sorts.get(0).getResolvedField());
        boolean desc = sorts != null && !sorts.isEmpty() && "DESC".equalsIgnoreCase(sorts.get(0).getDirection());
        String dir = desc ? " desc" : " asc";
        String expr = switch (key) {
            case "doc" -> "p.doc_number" + dir;
            case "age" -> "p.birth_date" + (desc ? " asc" : " desc") + " nulls last";                             // más edad = más antigua
            case "status" -> "p.status" + dir;
            case "branch" -> "lower(b.name)" + dir;
            case "createdAt" -> "p.created_at" + dir;
            default -> "lower(p.last_name)" + dir + ", lower(p.first_name)" + dir;
        };
        return expr + ", p.id";
    }

    private String encrypt(String plain) {
        return hasText(plain) ? cipher.encrypt(plain.trim()) : null;
    }

    private String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            return cipher.decrypt(stored);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Map<String, Object> diff(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return hasText(s) ? s.trim() : null;
    }
}
