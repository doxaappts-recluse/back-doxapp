package pe.dcs.app.features.person.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.person.dto.PersonDtos;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Núcleo 01 §2 · ConsentService. ÚNICO punto para otorgar, revocar y consultar consentimientos de una persona (M06, M07, M24).
 * Finalidades: DATA_PROCESSING (tratamiento de datos), COMMUNICATIONS (comunicaciones) y DIRECTORY (directorio del portal).
 * Hay un solo consentimiento vigente por persona y finalidad; revocar conserva el historial. [V15] sin COMMUNICATIONS vigente,
 * M19 no envía comunicaciones masivas a esa persona ({@link #hasValid}).
 */
@Service
@RequiredArgsConstructor
public class ConsentService {

    public static final String DATA_PROCESSING = "DATA_PROCESSING";
    public static final String COMMUNICATIONS = "COMMUNICATIONS";
    public static final String DIRECTORY = "DIRECTORY";
    public static final List<String> PURPOSES = List.of(DATA_PROCESSING, COMMUNICATIONS, DIRECTORY);
    private static final Set<String> SOURCES = Set.of("STAFF", "PUBLIC_FORM", "PORTAL", "IMPORT", "VISITOR");

    private final NamedParameterJdbcTemplate jdbc;
    private final PersonLookupService lookup;
    private final AuditService audit;
    private final Clock clock;

    @Value("${app.consent.version:v1}")
    private String version;

    // ---------------------------------------------------------------- otros módulos

    @Transactional(readOnly = true)
    public boolean hasValid(UUID personId, String purpose) {
        Long n = jdbc.queryForObject("select count(*) from consent_record where person_id = :p and purpose_code = :c and revoked_at is null",
                new MapSqlParameterSource("p", personId).addValue("c", purpose), Long.class);
        return n != null && n > 0;
    }

    /** Otorga las finalidades básicas (tratamiento de datos y comunicaciones) sin auditar por separado: lo usa el alta. */
    @Transactional
    public void grantDefaults(UUID orgId, UUID personId, String source, UUID by) {
        grant(orgId, personId, DATA_PROCESSING, source, by);
        grant(orgId, personId, COMMUNICATIONS, source, by);
    }

    /** Otorga una finalidad; si ya hay una vigente no hace nada. Devuelve si se creó. */
    @Transactional
    public boolean grant(UUID orgId, UUID personId, String purpose, String source, UUID by) {
        checkPurpose(purpose);
        if (!SOURCES.contains(source)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "origen");
        }
        if (hasValid(personId, purpose)) {
            return false;
        }
        jdbc.update("insert into consent_record (id, organization_id, person_id, purpose_code, version, granted_at, source, granted_by)"
                        + " values (:id, :o, :p, :c, :v, :at, :s, :by)",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("o", orgId).addValue("p", personId).addValue("c", purpose)
                        .addValue("v", version).addValue("at", Timestamp.from(clock.instant())).addValue("s", source).addValue("by", by));
        return true;
    }

    /** Revoca la finalidad vigente. Devuelve si había algo que revocar. */
    @Transactional
    public boolean revoke(UUID personId, String purpose, UUID by) {
        checkPurpose(purpose);
        int n = jdbc.update("update consent_record set revoked_at = :at, revoked_by = :by where person_id = :p and purpose_code = :c and revoked_at is null",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", by).addValue("p", personId).addValue("c", purpose));
        return n > 0;
    }

    /** Revoca todo (lo usa la anonimización). */
    @Transactional
    public void revokeAll(UUID personId, UUID by) {
        jdbc.update("update consent_record set revoked_at = :at, revoked_by = :by where person_id = :p and revoked_at is null",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", by).addValue("p", personId));
    }

    // ---------------------------------------------------------------- API de Personas

    @Transactional(readOnly = true)
    public PersonDtos.Consents consents(AccessScope scope, UUID personId) {
        lookup.getVisible(scope, personId);
        return view(personId);
    }

    /** Cambia una finalidad desde la ficha (acción E de Personas). Persona fusionada: no admite cambios. */
    @Transactional
    public PersonDtos.Consents set(AccessScope scope, UUID actorPerson, UUID personId, String purpose, boolean granted) {
        PersonLookupService.PersonMin p = lookup.getVisible(scope, personId);
        checkPurpose(purpose);
        if (lookup.isAnonymized(personId)) {
            throw new Exceptions("error.person.anonymized", HttpStatus.CONFLICT);
        }
        if ("MERGED".equals(p.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, p.status());
        }
        boolean changed = granted ? grant(scope.organizationId(), personId, purpose, "STAFF", actorPerson) : revoke(personId, purpose, actorPerson);
        if (changed) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("purpose", purpose);
            d.put("granted", granted);
            audit.record(new AuditService.Command("PERSON", granted ? "CONSENT_GRANT" : "CONSENT_REVOKE", "Person", personId, scope.organizationId(), null, d));
        }
        return view(personId);
    }

    PersonDtos.Consents view(UUID personId) {
        List<PersonDtos.ConsentRecord> history = jdbc.query("""
                select purpose_code, version, granted_at, source, revoked_at from consent_record where person_id = :p
                 order by granted_at desc, created_at desc""", new MapSqlParameterSource("p", personId), (rs, i) -> {
            Timestamp rev = rs.getTimestamp("revoked_at");
            return new PersonDtos.ConsentRecord(rs.getString("purpose_code"), rs.getString("version"), rs.getTimestamp("granted_at").toInstant(),
                    rs.getString("source"), rev == null ? null : rev.toInstant());
        });
        List<PersonDtos.ConsentPurpose> current = new ArrayList<>();
        for (String code : PURPOSES) {
            PersonDtos.ConsentRecord valid = history.stream().filter(h -> h.purpose().equals(code) && h.revokedAt() == null).findFirst().orElse(null);
            current.add(new PersonDtos.ConsentPurpose(code, valid != null, valid == null ? null : valid.grantedAt(), valid == null ? null : valid.version(),
                    valid == null ? null : valid.source()));
        }
        return new PersonDtos.Consents(current, history);
    }

    private static void checkPurpose(String purpose) {
        if (purpose == null || !PURPOSES.contains(purpose)) {
            throw new Exceptions("error.person.consentPurposeInvalid", HttpStatus.BAD_REQUEST);
        }
    }
}
