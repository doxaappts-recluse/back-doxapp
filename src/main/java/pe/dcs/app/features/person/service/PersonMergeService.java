package pe.dcs.app.features.person.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.auth.service.SecretCipher;
import pe.dcs.app.features.person.domain.Person;
import pe.dcs.app.features.person.domain.PersonRepository;
import pe.dcs.app.features.person.dto.PersonDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.storage.FileStorageService;
import pe.dcs.app.shared.vo.Address;
import pe.dcs.app.util.Exceptions;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * M06 · Fusión de duplicados [V11]. La persona {@code source} (duplicada) queda como MERGED apuntando a {@code target} (la que se
 * conserva); sus sedes anteriores, etiquetas, hogar, autorizaciones, accesos, foto y notas pasan a {@code target}, y los demás
 * módulos migran sus registros mediante {@link PersonMergeParticipant}. Es irreversible y queda auditada en ambas fichas.
 */
@Service
@RequiredArgsConstructor
public class PersonMergeService {

    private static final String MODULE = "PERSON";
    private static final String ENTITY = "Person";
    private static final List<String> FIELDS = List.of("document", "firstName", "lastName", "sex", "birthDate", "maritalStatus", "email",
            "phone", "whatsapp", "address", "occupation", "joinedAt");
    private static final int ADULT_AGE = 18;
    private static final int MAX_NOTES = 4000;

    private final PersonRepository persons;
    private final PersonLookupService lookup;
    private final PersonService personService;
    private final NamedParameterJdbcTemplate jdbc;
    private final SecretCipher cipher;
    private final AuditService audit;
    private final Clock clock;
    private final FileStorageService storage;
    private final MessageSource messages;
    private final List<PersonMergeParticipant> participants;

    // ---------------------------------------------------------------- vista previa

    @Transactional(readOnly = true)
    public PersonDtos.MergePreview preview(AccessScope scope, UUID sourceId, UUID targetId) {
        Person s = load(scope, sourceId, targetId);
        Person t = load(scope, targetId, sourceId);
        return new PersonDtos.MergePreview(side(s), side(t), fields(s, t), impact(s, t), blockers(s, t));
    }

    // ---------------------------------------------------------------- fusión

    @Transactional
    public PersonDtos.Response merge(AuthenticatedActor actor, AccessScope scope, UUID sourceId, PersonDtos.MergeRequest req) {
        if (req == null || req.targetId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona a conservar");
        }
        Person s = load(scope, sourceId, req.targetId());
        Person t = load(scope, req.targetId(), sourceId);
        if ((req.sourceVersion() != null && !req.sourceVersion().equals(s.getVersion()))
                || (req.targetVersion() != null && !req.targetVersion().equals(t.getVersion()))) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        List<PersonDtos.MergeBlocker> blockers = blockers(s, t);
        if (!blockers.isEmpty()) {
            throw new Exceptions("error.person.mergeBlocked", HttpStatus.UNPROCESSABLE_ENTITY, blockers.get(0).message());
        }
        Map<String, Boolean> fromSource = choose(fields(s, t), req.choices());

        // 1. valores finales de la ficha que se conserva (se leen antes de vaciar la duplicada)
        FinalValues v = new FinalValues(s, t, fromSource);
        if (!minor(v.birthDate) && v.phone == null && v.email == null) {
            throw new Exceptions("error.person.contactRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String notes = combine(decrypt(t.getPrivateNotes()), decrypt(s.getPrivateNotes()));
        String allergies = combine(decrypt(t.getAllergies()), decrypt(s.getAllergies()));
        UUID org = scope.organizationId();
        LocalDate today = LocalDate.now(clock);
        Timestamp now = Timestamp.from(clock.instant());
        List<PersonDtos.MergeImpact> impact = impact(s, t);

        // 2. registros propios de Personas
        MapSqlParameterSource ps = new MapSqlParameterSource("s", s.getId()).addValue("t", t.getId()).addValue("today", java.sql.Date.valueOf(today))
                .addValue("now", now).addValue("by", scope.personId()).addValue("o", org);
        jdbc.update("update person_branch set is_current = false, to_date = coalesce(to_date, :today) where person_id = :s and is_current", ps);
        jdbc.update("update person_branch set person_id = :t where person_id = :s", ps);
        jdbc.update("insert into person_tag (person_id, tag_id, created_at, created_by) select :t, tag_id, created_at, created_by from person_tag"
                + " where person_id = :s on conflict do nothing", ps);
        jdbc.update("delete from person_tag where person_id = :s", ps);
        moveHousehold(s.getId(), t.getId(), today);
        jdbc.update("update consent_record c set revoked_at = :now, revoked_by = :by where c.person_id = :s and c.revoked_at is null and exists"
                + " (select 1 from consent_record x where x.person_id = :t and x.purpose_code = c.purpose_code and x.revoked_at is null)", ps);
        jdbc.update("update consent_record set person_id = :t where person_id = :s", ps);
        jdbc.update("update user_access set person_id = :t where person_id = :s", ps);
        jdbc.update("update credential set person_id = :t where person_id = :s", ps);

        // 3. otros módulos
        for (PersonMergeParticipant p : participants) {
            p.migrate(org, s.getId(), t.getId());
        }

        // 4. foto: se conserva la de la ficha principal; si no tiene, pasa la de la duplicada
        String sourcePhoto = s.getPhotoKey();
        if (sourcePhoto != null) {
            if (t.getPhotoKey() == null) {
                FileStorageService.StoredFile f = storage.get(sourcePhoto).orElse(null);
                if (f != null) {
                    String key = "org/" + org + "/persons/" + t.getId() + "/photo.jpg";
                    storage.put(key, f.data(), "image/jpeg");
                    t.setPhotoKey(key);
                    t.setPhotoUpdatedAt(clock.instant());
                }
            }
            storage.delete(sourcePhoto);
            s.setPhotoKey(null);
            s.setPhotoUpdatedAt(null);
        }

        // 5. la duplicada: pierde documento y datos de contacto (liberan el documento y ya no se buscan) y queda MERGED
        String sourceDocType = s.getDocType() == null ? null : s.getDocType().name();
        s.setDocType(null);
        s.setDocNumber(null);
        s.setEmail(null);
        s.setPhone(null);
        s.setWhatsapp(null);
        s.setAddress(new Address());
        s.setOccupation(null);
        s.setPrivateNotes(null);
        s.setAllergies(null);
        s.setStatus("MERGED");
        s.setMergedInto(t.getId());
        s.setStatusReason(null);
        persons.saveAndFlush(s);

        // 6. la que se conserva recibe los valores elegidos
        t.setDocType(v.docType);
        t.setDocNumber(v.docNumber);
        t.setFirstName(v.firstName);
        t.setLastName(v.lastName);
        t.setSex(v.sex);
        t.setBirthDate(v.birthDate);
        t.setMaritalStatus(v.marital);
        t.setEmail(v.email);
        t.setPhone(v.phone);
        t.setWhatsapp(v.whatsapp);
        t.setAddress(v.address);
        t.setOccupation(v.occupation);
        t.setJoinedAt(v.joinedAt);
        t.setPrivateNotes(notes == null ? null : cipher.encrypt(notes));
        t.setAllergies(allergies == null ? null : cipher.encrypt(allergies));
        persons.saveAndFlush(t);

        Map<String, Object> d = new LinkedHashMap<>();
        d.put("sourceId", s.getId());
        d.put("fromSource", fromSource.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList());
        d.put("impact", impact.stream().filter(i -> i.count() > 0).collect(java.util.stream.Collectors.toMap(PersonDtos.MergeImpact::key,
                PersonDtos.MergeImpact::count, (a, b) -> a, LinkedHashMap::new)));
        d.put("sourceDocType", sourceDocType);
        audit.record(new AuditService.Command(MODULE, "MERGE", ENTITY, t.getId(), org, t.getPrimaryBranchId(), d));
        audit.record(new AuditService.Command(MODULE, "MERGED_INTO", ENTITY, s.getId(), org, s.getPrimaryBranchId(), Map.of("targetId", t.getId())));
        return personService.get(actor, scope, t.getId());
    }

    // ---------------------------------------------------------------- validaciones y cálculo

    /** Carga una persona visible y comprueba [V11]: distintas, misma organización y ninguna fusionada ni anonimizada. */
    private Person load(AccessScope scope, UUID id, UUID other) {
        if (id == null || other == null || id.equals(other)) {
            throw new Exceptions("error.person.mergeInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        lookup.getVisible(scope, id);
        Person p = persons.findByIdAndOrganizationId(id, scope.organizationId())
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        if ("MERGED".equals(p.getStatus()) || p.getAnonymizedAt() != null) {
            throw new Exceptions("error.person.mergeInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return p;
    }

    private PersonDtos.MergeSide side(Person p) {
        String branch = p.getPrimaryBranchId() == null ? null : jdbc.query("select name from branch where id = :id",
                new MapSqlParameterSource("id", p.getPrimaryBranchId()), (rs, i) -> rs.getString(1)).stream().findFirst().orElse(null);
        Boolean access = jdbc.queryForObject("select exists(select 1 from user_access where person_id = :p and status in ('ACTIVE','INVITED'))",
                new MapSqlParameterSource("p", p.getId()), Boolean.class);
        return new PersonDtos.MergeSide(p.getId(), p.fullName(), p.getDocType() == null ? null : p.getDocType().name(), p.getDocNumber(),
                p.getStatus(), branch, Boolean.TRUE.equals(access), p.getPhotoKey() != null, p.getVersion());
    }

    private List<PersonDtos.MergeField> fields(Person s, Person t) {
        List<PersonDtos.MergeField> out = new ArrayList<>();
        for (String f : FIELDS) {
            String sv = value(s, f);
            String tv = value(t, f);
            boolean differs = !Objects.equals(sv, tv);
            String proposed = differs && tv == null && sv != null ? "SOURCE" : "TARGET";
            out.add(new PersonDtos.MergeField(f, sv, tv, differs, proposed));
        }
        return out;
    }

    private static String value(Person p, String f) {
        return switch (f) {
            case "document" -> p.getDocType() == null || p.getDocNumber() == null ? null : p.getDocType().name() + " " + p.getDocNumber();
            case "firstName" -> p.getFirstName();
            case "lastName" -> p.getLastName();
            case "sex" -> p.getSex();
            case "birthDate" -> p.getBirthDate() == null ? null : p.getBirthDate().toString();
            case "maritalStatus" -> p.getMaritalStatus();
            case "email" -> p.getEmail();
            case "phone" -> p.getPhone();
            case "whatsapp" -> p.getWhatsapp();
            case "address" -> address(p.getAddress());
            case "occupation" -> p.getOccupation();
            case "joinedAt" -> p.getJoinedAt() == null ? null : p.getJoinedAt().toString();
            default -> null;
        };
    }

    private static String address(Address a) {
        if (a == null) {
            return null;
        }
        String s = java.util.stream.Stream.of(a.getLine(), a.getDistrict(), a.getCity(), a.getRegion())
                .filter(x -> x != null && !x.isBlank()).collect(java.util.stream.Collectors.joining(", "));
        return s.isEmpty() ? null : s;
    }

    /** Campo → true si se toma de la persona duplicada. Valida las claves y los valores enviados. */
    private static Map<String, Boolean> choose(List<PersonDtos.MergeField> fields, Map<String, String> choices) {
        Set<String> known = new java.util.HashSet<>(FIELDS);
        if (choices != null) {
            for (Map.Entry<String, String> e : choices.entrySet()) {
                String v = e.getValue() == null ? "" : e.getValue().trim().toUpperCase(Locale.ROOT);
                if (!known.contains(e.getKey()) || !(v.equals("SOURCE") || v.equals("TARGET"))) {
                    throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "campo de fusión");
                }
            }
        }
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (PersonDtos.MergeField f : fields) {
            String c = choices == null ? null : choices.get(f.field());
            String pick = c == null ? f.proposed() : c.trim().toUpperCase(Locale.ROOT);
            out.put(f.field(), "SOURCE".equals(pick));
        }
        return out;
    }

    /** Valores que tendrá la ficha que se conserva. */
    private static final class FinalValues {
        final pe.dcs.app.shared.vo.DocumentType docType;
        final String docNumber, firstName, lastName, sex, marital, email, phone, whatsapp, occupation;
        final LocalDate birthDate, joinedAt;
        final Address address;

        FinalValues(Person s, Person t, Map<String, Boolean> src) {
            Person doc = src.get("document") ? s : t;
            docType = doc.getDocType();
            docNumber = doc.getDocNumber();
            firstName = (src.get("firstName") ? s : t).getFirstName();
            lastName = (src.get("lastName") ? s : t).getLastName();
            sex = (src.get("sex") ? s : t).getSex();
            birthDate = (src.get("birthDate") ? s : t).getBirthDate();
            marital = (src.get("maritalStatus") ? s : t).getMaritalStatus();
            email = (src.get("email") ? s : t).getEmail();
            phone = (src.get("phone") ? s : t).getPhone();
            whatsapp = (src.get("whatsapp") ? s : t).getWhatsapp();
            Address a = (src.get("address") ? s : t).getAddress();
            address = copy(a);
            occupation = (src.get("occupation") ? s : t).getOccupation();
            joinedAt = (src.get("joinedAt") ? s : t).getJoinedAt();
        }

        private static Address copy(Address a) {
            Address n = new Address();
            if (a != null) {
                n.setLine(a.getLine());
                n.setDistrict(a.getDistrict());
                n.setCity(a.getCity());
                n.setRegion(a.getRegion());
                n.setCountry(a.getCountry());
                n.setReference(a.getReference());
            }
            return n;
        }
    }

    private boolean minor(LocalDate birth) {
        return birth != null && java.time.Period.between(birth, LocalDate.now(clock)).getYears() < ADULT_AGE;
    }

    private List<PersonDtos.MergeImpact> impact(Person s, Person t) {
        MapSqlParameterSource ps = new MapSqlParameterSource("s", s.getId()).addValue("t", t.getId());
        List<PersonDtos.MergeImpact> out = new ArrayList<>();
        out.add(new PersonDtos.MergeImpact("branchPeriods", count("select count(*) from person_branch where person_id = :s", ps)));
        out.add(new PersonDtos.MergeImpact("tags", count("select count(*) from person_tag x where x.person_id = :s and not exists"
                + " (select 1 from person_tag y where y.person_id = :t and y.tag_id = x.tag_id)", ps)));
        out.add(new PersonDtos.MergeImpact("household", count("select count(*) from household_member where person_id = :s and left_at is null", ps)));
        out.add(new PersonDtos.MergeImpact("consents", count("select count(*) from consent_record where person_id = :s", ps)));
        out.add(new PersonDtos.MergeImpact("accesses", count("select count(*) from user_access where person_id = :s", ps)));
        out.add(new PersonDtos.MergeImpact("credential", count("select count(*) from credential where person_id = :s", ps)));
        out.add(new PersonDtos.MergeImpact("photo", s.getPhotoKey() != null && t.getPhotoKey() == null ? 1 : 0));
        for (PersonMergeParticipant p : participants) {
            out.add(new PersonDtos.MergeImpact(p.key(), p.count(s.getOrganizationId(), s.getId())));
        }
        return out;
    }

    private List<PersonDtos.MergeBlocker> blockers(Person s, Person t) {
        List<PersonDtos.MergeBlocker> out = new ArrayList<>();
        MapSqlParameterSource ps = new MapSqlParameterSource("s", s.getId()).addValue("t", t.getId());
        if (count("select count(*) from credential where person_id = :s", ps) > 0 && count("select count(*) from credential where person_id = :t", ps) > 0) {
            out.add(blocker("error.person.mergeBothCredentials"));
        }
        if (count("select count(*) from user_access a join user_access b on b.person_id = :t and b.organization_id = a.organization_id"
                + " and coalesce(b.branch_id, '00000000-0000-0000-0000-000000000000'::uuid) = coalesce(a.branch_id, '00000000-0000-0000-0000-000000000000'::uuid)"
                + " and b.role = a.role where a.person_id = :s", ps) > 0) {
            out.add(blocker("error.person.mergeBothAccess"));
        }
        List<Map<String, Object>> sm = jdbc.queryForList("select household_id, role from household_member where person_id = :s and left_at is null", ps);
        if (!sm.isEmpty() && "HEAD".equals(sm.get(0).get("role"))) {
            UUID hid = (UUID) sm.get(0).get("household_id");
            boolean targetHere = count("select count(*) from household_member where person_id = :t and left_at is null and household_id = :h",
                    ps.addValue("h", hid)) > 0;
            boolean targetElsewhere = count("select count(*) from household_member where person_id = :t and left_at is null", ps) > 0;
            boolean others = count("select count(*) from household_member where household_id = :h and left_at is null and person_id <> :s", ps) > 0;
            if (targetElsewhere && !targetHere && others) {
                out.add(blocker("error.person.mergeHouseholdHead"));
            }
        }
        for (PersonMergeParticipant p : participants) {
            String key = p.blocker(s.getOrganizationId(), s.getId(), t.getId());
            if (key != null) {
                out.add(blocker(key));
            }
        }
        return out;
    }

    private PersonDtos.MergeBlocker blocker(String key) {
        return new PersonDtos.MergeBlocker(key, messages.getMessage(key, null, key, LocaleContextHolder.getLocale()));
    }

    /** Hogar: si el destino no tiene hogar, hereda el de la duplicada; si ya está en otro, la duplicada sale del suyo. */
    private void moveHousehold(UUID s, UUID t, LocalDate today) {
        MapSqlParameterSource ps = new MapSqlParameterSource("s", s).addValue("t", t).addValue("today", java.sql.Date.valueOf(today));
        List<Map<String, Object>> sm = jdbc.queryForList("select id, household_id, role, guardian from household_member where person_id = :s and left_at is null", ps);
        if (sm.isEmpty()) {
            return;
        }
        Map<String, Object> m = sm.get(0);
        UUID rowId = (UUID) m.get("id");
        UUID hid = (UUID) m.get("household_id");
        String role = (String) m.get("role");
        boolean guardian = Boolean.TRUE.equals(m.get("guardian"));
        List<Map<String, Object>> tm = jdbc.queryForList("select id, household_id from household_member where person_id = :t and left_at is null", ps);
        ps.addValue("id", rowId).addValue("h", hid);
        if (tm.isEmpty()) {
            jdbc.update("update household_member set person_id = :t where id = :id", ps);
            return;
        }
        jdbc.update("update household_member set left_at = :today where id = :id", ps);
        if (hid.equals(tm.get(0).get("household_id"))) {
            if ("HEAD".equals(role)) {
                jdbc.update("update household_member set role = 'HEAD' where person_id = :t and left_at is null", ps);
            }
            if (guardian) {
                jdbc.update("update household_member set guardian = true where person_id = :t and left_at is null", ps);
            }
        }
    }

    private int count(String sql, MapSqlParameterSource ps) {
        Integer n = jdbc.queryForObject(sql, ps, Integer.class);
        return n == null ? 0 : n;
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

    /** Une dos textos reservados sin perder ninguno. */
    private static String combine(String keep, String other) {
        if (other == null || other.isBlank()) {
            return keep;
        }
        if (keep == null || keep.isBlank()) {
            return other;
        }
        if (keep.equals(other)) {
            return keep;
        }
        String s = keep + "\n---\n" + other;
        return s.length() > MAX_NOTES ? s.substring(0, MAX_NOTES) : s;
    }
}
