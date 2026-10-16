package pe.dcs.app.features.person.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.person.domain.Person;
import pe.dcs.app.features.person.domain.PersonRepository;
import pe.dcs.app.features.person.dto.PersonDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.storage.FileStorageService;
import pe.dcs.app.shared.vo.Address;
import pe.dcs.app.shared.vo.DocumentValidator;
import pe.dcs.app.util.Exceptions;

import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * M06 · Anonimización [V12]: borra la identidad de una persona conservando los agregados (sede, sexo, año de nacimiento, fechas y
 * conteos). Exige la acción P y que se escriba su documento (o su nombre completo si no tiene documento) como confirmación.
 * Es irreversible. Las personas con acceso activo al sistema no se pueden anonimizar: primero se desactiva el acceso.
 *
 * <p>Límite conocido: la auditoría es de solo agregar y no se reescribe; los eventos ya registrados no guardan datos personales
 * salvo los motivos escritos a mano (por ejemplo, el motivo de un cambio de documento).
 */
@Service
@RequiredArgsConstructor
public class PersonAnonymizeService {

    static final String FIRST_NAME = "Anónimo";
    static final String LAST_NAME = "(anonimizado)";

    private final PersonRepository persons;
    private final PersonLookupService lookup;
    private final PersonService personService;
    private final ConsentService consents;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;
    private final FileStorageService storage;
    private final List<PersonMergeParticipant> participants;

    @Transactional
    public PersonDtos.Response anonymize(AuthenticatedActor actor, AccessScope scope, UUID id, PersonDtos.AnonymizeRequest req) {
        lookup.getVisible(scope, id);
        Person p = persons.findByIdAndOrganizationId(id, scope.organizationId())
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        if (p.getAnonymizedAt() != null) {
            throw new Exceptions("error.person.anonymized", HttpStatus.CONFLICT);
        }
        if (req != null && req.version() != null && !req.version().equals(p.getVersion())) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        if (!confirmed(p, req == null ? null : req.confirmation())) {
            throw new Exceptions("error.person.anonymizeConfirm", HttpStatus.BAD_REQUEST);
        }
        MapSqlParameterSource ps = new MapSqlParameterSource("p", p.getId()).addValue("o", p.getOrganizationId());
        Boolean access = jdbc.queryForObject("select exists(select 1 from user_access where person_id = :p and status in ('ACTIVE','INVITED'))", ps, Boolean.class);
        if (Boolean.TRUE.equals(access)) {
            throw new Exceptions("error.person.hasActiveAccess", HttpStatus.CONFLICT);
        }
        List<Map<String, Object>> hm = jdbc.queryForList("select id, household_id, role from household_member where person_id = :p and left_at is null", ps);
        if (!hm.isEmpty()) {
            MapSqlParameterSource hp = new MapSqlParameterSource("p", p.getId()).addValue("h", hm.get(0).get("household_id"));
            Integer others = jdbc.queryForObject("select count(*) from household_member where household_id = :h and left_at is null and person_id <> :p", hp, Integer.class);
            if ("HEAD".equals(hm.get(0).get("role")) && others != null && others > 0) {
                throw new Exceptions("error.person.anonymizeHousehold", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }

        LocalDate today = LocalDate.now(clock);
        Timestamp now = Timestamp.from(clock.instant());
        ps.addValue("today", java.sql.Date.valueOf(today)).addValue("now", now).addValue("by", scope.personId());
        UUID org = p.getOrganizationId();

        jdbc.update("update household_member set left_at = :today where person_id = :p and left_at is null", ps);
        jdbc.update("delete from person_tag where person_id = :p", ps);
        consents.revokeAll(p.getId(), scope.personId());
        jdbc.update("update credential set username = 'anonimo-' || substr(cast(id as varchar), 1, 8), password_hash = null, mfa_secret_enc = null,"
                + " mfa_enabled = false, status = 'INACTIVE', updated_at = :now where person_id = :p", ps);
        for (PersonMergeParticipant m : participants) {
            m.anonymize(org, p.getId());
        }
        if (p.getPhotoKey() != null) {
            storage.delete(p.getPhotoKey());
        }

        p.setFirstName(FIRST_NAME);
        p.setLastName(LAST_NAME);
        p.setDocType(null);
        p.setDocNumber(null);
        p.setEmail(null);
        p.setPhone(null);
        p.setWhatsapp(null);
        p.setAddress(new Address());
        p.setOccupation(null);
        p.setMaritalStatus(null);
        p.setPrivateNotes(null);
        p.setAllergies(null);
        p.setPhotoKey(null);
        p.setPhotoUpdatedAt(null);
        if (p.getBirthDate() != null) {
            p.setBirthDate(LocalDate.of(p.getBirthDate().getYear(), 1, 1));                                // solo se conserva el año
        }
        if ("ACTIVE".equals(p.getStatus())) {
            p.setStatus("INACTIVE");
        }
        p.setStatusReason(null);
        p.setAnonymizedAt(clock.instant());
        persons.saveAndFlush(p);

        audit.record(new AuditService.Command("PERSON", "ANONYMIZE", "Person", p.getId(), org, p.getPrimaryBranchId(), Map.of()));
        return personService.get(actor, scope, p.getId());
    }

    /** [V12] El texto debe ser el documento de la persona o, si no tiene, su nombre completo (sin importar mayúsculas ni tildes). */
    private static boolean confirmed(Person p, String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (p.getDocNumber() != null) {
            return DocumentValidator.normalize(text).equalsIgnoreCase(p.getDocNumber());
        }
        return fold(text).equals(fold(p.fullName()));
    }

    private static String fold(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
