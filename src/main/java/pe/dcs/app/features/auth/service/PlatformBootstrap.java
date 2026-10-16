package pe.dcs.app.features.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import pe.dcs.app.features.access.domain.AccessStatus;
import pe.dcs.app.features.access.domain.ActorType;
import pe.dcs.app.features.access.domain.Credential;
import pe.dcs.app.features.access.domain.PlatformStaff;
import pe.dcs.app.features.access.domain.StaffRole;
import pe.dcs.app.features.access.repo.CredentialRepository;
import pe.dcs.app.features.access.repo.PlatformStaffRepository;
import pe.dcs.app.shared.vo.DocumentType;

import java.security.SecureRandom;
import java.time.Clock;

/**
 * Primer SYSTEM_ADMIN (spec 00 §5: no existe contraseña por defecto en el código). Solo actúa si no hay ningún personal
 * de plataforma y {@code app.bootstrap.email} está definido. Si no se da contraseña se genera una aleatoria y se escribe
 * UNA vez en el log de arranque. La cuenta nace con cambio de contraseña y MFA obligatorios en el primer ingreso.
 */
@Slf4j
@Component
public class PlatformBootstrap implements ApplicationRunner {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";

    private final PlatformStaffRepository staffRepository;
    private final CredentialRepository credentials;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final TransactionTemplate tx;
    private final Clock clock;

    private final boolean enabled;
    private final String email;
    private final String password;
    private final String firstName;
    private final String lastName;
    private final String docType;
    private final String docNumber;

    public PlatformBootstrap(PlatformStaffRepository staffRepository, CredentialRepository credentials,
                             PasswordEncoder encoder, PasswordPolicy policy, TransactionTemplate tx, Clock clock,
                             @Value("${app.bootstrap.enabled:true}") boolean enabled,
                             @Value("${app.bootstrap.email:}") String email,
                             @Value("${app.bootstrap.password:}") String password,
                             @Value("${app.bootstrap.first-name:Admin}") String firstName,
                             @Value("${app.bootstrap.last-name:Sistema}") String lastName,
                             @Value("${app.bootstrap.doc-type:DNI}") String docType,
                             @Value("${app.bootstrap.doc-number:}") String docNumber) {
        this.staffRepository = staffRepository;
        this.credentials = credentials;
        this.encoder = encoder;
        this.policy = policy;
        this.tx = tx;
        this.clock = clock;
        this.enabled = enabled;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.docType = docType;
        this.docNumber = docNumber;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || staffRepository.count() > 0) {
            return;
        }
        if (email == null || email.isBlank()) {
            log.warn("No hay personal de plataforma y BOOTSTRAP_EMAIL no está definido: no se creó el primer SYSTEM_ADMIN.");
            return;
        }
        boolean generated = password == null || password.isBlank();
        String initial = generated ? randomPassword() : password;
        if (!generated && !policy.isValid(initial, email)) {
            throw new IllegalStateException("BOOTSTRAP_PASSWORD no cumple la política (≥10 caracteres, mayúscula, minúscula y número)");
        }
        tx.executeWithoutResult(status -> {
            PlatformStaff s = new PlatformStaff();
            s.setFirstName(firstName);
            s.setLastName(lastName);
            boolean hasDoc = docNumber != null && !docNumber.isBlank();
            s.setDocType(hasDoc ? DocumentType.valueOf(docType.trim().toUpperCase()) : DocumentType.PASSPORT);
            s.setDocNumber(hasDoc ? docNumber.trim() : "BOOT" + (10000000 + new SecureRandom().nextInt(89999999)));
            s.setEmail(email.trim());
            s.setStaffRole(StaffRole.SYSTEM_ADMIN);
            s.setStatus(AccessStatus.ACTIVE);
            staffRepository.save(s);

            Credential c = new Credential();
            c.setActorType(ActorType.STAFF);
            c.setStaffId(s.getId());
            c.setUsername(email.trim());
            c.setPasswordHash(encoder.encode(initial));
            c.setStatus(AccessStatus.ACTIVE);
            c.setMustChangePassword(true);
            c.setPasswordChangedAt(clock.instant());
            credentials.save(c);
        });
        log.warn("Primer SYSTEM_ADMIN creado: usuario={} (cambio de contraseña y MFA obligatorios en el primer ingreso)", email);
        if (generated) {
            log.warn("Contraseña inicial GENERADA (se muestra una sola vez): {}", initial);
        }
    }

    private static String randomPassword() {
        SecureRandom r = new SecureRandom();
        String all = UPPER + LOWER + DIGITS;
        StringBuilder sb = new StringBuilder();
        sb.append(UPPER.charAt(r.nextInt(UPPER.length())));
        sb.append(LOWER.charAt(r.nextInt(LOWER.length())));
        sb.append(DIGITS.charAt(r.nextInt(DIGITS.length())));
        while (sb.length() < 16) {
            sb.append(all.charAt(r.nextInt(all.length())));
        }
        return sb.toString();
    }
}
