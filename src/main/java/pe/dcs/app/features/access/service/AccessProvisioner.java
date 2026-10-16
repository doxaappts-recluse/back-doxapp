package pe.dcs.app.features.access.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.access.domain.AccessStatus;
import pe.dcs.app.features.access.domain.ActorType;
import pe.dcs.app.features.access.domain.Credential;
import pe.dcs.app.features.access.domain.UserAccess;
import pe.dcs.app.features.access.repo.CredentialRepository;
import pe.dcs.app.features.access.repo.UserAccessRepository;
import pe.dcs.app.features.auth.service.InvitationService;
import pe.dcs.app.features.organization.domain.Organization;
import pe.dcs.app.features.person.domain.Person;
import pe.dcs.app.features.person.domain.PersonRepository;
import pe.dcs.app.shared.vo.ContactValidator;
import pe.dcs.app.shared.vo.DocumentType;
import pe.dcs.app.shared.vo.DocumentValidator;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Da de alta a la persona, su credencial y el acceso (compartido por la plataforma —administradores de organización— y
 * por el equipo de la organización). La persona mínima nace aquí; M06 completa su ficha.
 * Invitación: solo si la credencial es nueva; una persona que ya tiene contraseña recibe el acceso ACTIVE de inmediato.
 */
@Component
@RequiredArgsConstructor
public class AccessProvisioner {

    /** Identidad de la persona: una existente o los datos mínimos para crearla. */
    public record PersonInput(UUID personId, DocumentType docType, String docNumber, String firstName, String lastName,
                              String email, String phone) {
    }

    public record Provisioned(UserAccess access, Person person, Credential credential, boolean invited) {
    }

    private final PersonRepository persons;
    private final CredentialRepository credentials;
    private final UserAccessRepository accesses;
    private final InvitationService invitations;

    /** Persona existente (404 si es de otra organización) o nueva; una persona con el mismo documento se reutiliza. */
    Person resolvePerson(UUID orgId, PersonInput in) {
        if (in.personId() != null) {
            return persons.findByIdAndOrganizationId(in.personId(), orgId)
                    .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        }
        if (in.docType() == null || isBlank(in.docNumber()) || isBlank(in.firstName()) || isBlank(in.lastName())) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona");
        }
        String doc = DocumentValidator.normalize(in.docNumber());
        if (in.docType() == DocumentType.RUC || !DocumentValidator.isValid(in.docType(), doc)) {
            throw new Exceptions("error.common.docInvalid", HttpStatus.BAD_REQUEST, doc, in.docType());
        }
        String email = isBlank(in.email()) ? null : ContactValidator.normalizeEmail(in.email());
        if (email != null && !ContactValidator.isValidEmail(email)) {
            throw new Exceptions("error.common.emailInvalid", HttpStatus.BAD_REQUEST);
        }
        String phone = isBlank(in.phone()) ? null : in.phone().trim();
        if (phone != null && !ContactValidator.isValidPhone(phone)) {
            throw new Exceptions("error.common.phoneInvalid", HttpStatus.BAD_REQUEST);
        }
        Person existing = persons.findByOrganizationIdAndDocTypeAndDocNumber(orgId, in.docType(), doc).orElse(null);
        if (existing != null) {
            if (existing.getEmail() == null && email != null) {
                existing.setEmail(email);                     // solo se completa lo que faltaba; nunca se pisa lo existente
                persons.save(existing);
            }
            return existing;
        }
        Person p = new Person();
        p.setOrganizationId(orgId);
        p.setDocType(in.docType());
        p.setDocNumber(doc);
        p.setFirstName(in.firstName().trim());
        p.setLastName(in.lastName().trim());
        p.setEmail(email);
        p.setPhone(phone);
        return persons.saveAndFlush(p);
    }

    /** Credencial de la persona: la existente o una nueva INVITED con el correo como usuario. */
    private Credential ensureCredential(Organization org, Person p, boolean[] created) {
        Credential c = credentials.findByPersonId(p.getId()).orElse(null);
        if (c != null) {
            return c;
        }
        if (isBlank(p.getEmail())) {
            throw new Exceptions("error.access.contactRequired", HttpStatus.UNPROCESSABLE_ENTITY);            // [V6]
        }
        String username = p.getEmail().trim().toLowerCase();
        credentials.findPersonByUsername(org.getId(), username).ifPresent(other -> {
            throw new Exceptions("error.access.emailDuplicate", HttpStatus.CONFLICT);
        });
        c = new Credential();
        c.setActorType(ActorType.PERSON);
        c.setPersonId(p.getId());
        c.setOrganizationId(org.getId());
        c.setUsername(username);
        c.setStatus(AccessStatus.INVITED);
        c.setMustChangePassword(false);
        created[0] = true;
        return credentials.saveAndFlush(c);
    }

    /** Crea el acceso (y la persona y credencial si hacen falta) y envía la invitación cuando corresponde. */
    Provisioned provision(Organization org, PersonInput person, RoleType role, UUID branchId, LocalDate validFrom, LocalDate validTo) {
        Person p = resolvePerson(org.getId(), person);
        if (accesses.duplicateExists(p.getId(), org.getId(), branchId, role.name())) {                         // [V9]
            throw new Exceptions("error.access.duplicate", HttpStatus.CONFLICT);
        }
        boolean[] created = {false};
        Credential c = ensureCredential(org, p, created);

        UserAccess a = new UserAccess();
        a.setOrganizationId(org.getId());
        a.setPersonId(p.getId());
        a.setBranchId(branchId);
        a.setRole(role);
        a.setValidFrom(validFrom);
        a.setValidTo(validTo);
        a.setStatus(c.getPasswordHash() == null ? AccessStatus.INVITED : AccessStatus.ACTIVE);
        a = accesses.saveAndFlush(a);

        if (created[0]) {
            sendInvite(org, p, c);
        }
        return new Provisioned(a, p, c, created[0]);
    }

    void sendInvite(Organization org, Person p, Credential c) {
        invitations.sendInvite(c.getId(), p.getEmail(), p.fullName(), org.getSlug(), LocaleContextHolder.getLocale().getLanguage());
    }

    /**
     * M24 (portal del miembro) · único punto de entrada público de esta clase para paquetes fuera de {@code access.service}:
     * da de alta el acceso MEMBER de una persona (no consume licencia, ver {@link UserAccessRepository#reservedInOrganization}).
     * Vigencia abierta (validTo null); {@code branchId} nunca null por {@code ck_ua_branch} — el llamador resuelve la sede
     * (invitación explícita del administrador o {@code primaryBranchId} de la persona en el alta por autorregistro).
     */
    public Provisioned provisionMember(Organization org, PersonInput person, UUID branchId) {
        return provision(org, person, RoleType.MEMBER, branchId, LocalDate.now(), null);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
