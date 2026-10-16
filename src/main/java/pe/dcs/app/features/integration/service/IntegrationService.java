package pe.dcs.app.features.integration.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pe.dcs.app.features.auth.service.SecretCipher;
import pe.dcs.app.features.config.service.OrgGuard;
import pe.dcs.app.features.integration.domain.IntegrationConfig;
import pe.dcs.app.features.integration.domain.IntegrationConfigRepository;
import pe.dcs.app.features.integration.domain.IntegrationProvider;
import pe.dcs.app.features.integration.dto.IntegrationDtos.Card;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * M23 · Integraciones de la organización. Los secretos se guardan cifrados (AES-256-GCM), son de solo escritura y no
 * aparecen en respuestas, registros ni auditoría [V8]; ni siquiera la plataforma los ve. Una integración pasa a ACTIVE
 * solo con una prueba exitosa de las últimas 24 h [V9] y, si es de correo, con el dominio verificado [V11].
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationService {

    private static final String MODULE = "INTEGRATIONS";
    private static final String ENTITY = "IntegrationConfig";
    private static final Duration TEST_VALIDITY = Duration.ofHours(24);
    private static final int MAX_FAILURES = 3;

    private final IntegrationConfigRepository repository;
    private final ObjectProvider<IntegrationTester> testers;
    private final DomainVerifier domains;
    private final SecretCipher cipher;
    private final ObjectMapper mapper;
    private final OrgGuard guard;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    // ---------------------------------------------------------------- lectura

    public List<Card> list(AuthenticatedActor actor) {
        return tx.execute(st -> {
            Map<IntegrationProvider, IntegrationConfig> rows = new EnumMap<>(IntegrationProvider.class);
            repository.findByOrganizationId(actor.organizationId()).forEach(r -> rows.put(r.getProvider(), r));
            return Arrays.stream(IntegrationProvider.values()).map(p -> card(p, rows.get(p))).toList();
        });
    }

    public Card get(AuthenticatedActor actor, IntegrationProvider provider) {
        return tx.execute(st -> card(provider, repository.findByOrganizationIdAndProvider(actor.organizationId(), provider).orElse(null)));
    }

    // ---------------------------------------------------------------- escritura

    /** Crea o actualiza la configuración. Cualquier cambio deja la integración en TESTING: hay que volver a probarla. */
    public Card save(AuthenticatedActor actor, IntegrationProvider provider, Map<String, Object> settings, Map<String, String> secrets) {
        guard.requireOrgAdmin(actor);
        return tx.execute(st -> {
            UUID org = actor.organizationId();
            guard.assertOpen(org);
            IntegrationConfig c = repository.findByOrganizationIdAndProvider(org, provider).orElse(null);
            boolean creating = c == null;
            if (creating) {
                c = new IntegrationConfig();
                c.setOrganizationId(org);
                c.setProvider(provider);
                c.setKind(provider.kind());
            }
            Map<String, Object> cleanSettings = cleanSettings(provider, settings);
            Map<String, String> stored = creating ? new HashMap<>() : readSecrets(c);
            List<String> changed = new ArrayList<>();
            if (secrets != null) {
                for (Map.Entry<String, String> e : secrets.entrySet()) {
                    IntegrationProvider.Field f = field(provider, e.getKey());
                    if (f == null || !f.secret()) {
                        throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, e.getKey());
                    }
                    if (e.getValue() != null && !e.getValue().isBlank()) {
                        stored.put(e.getKey(), e.getValue());
                        changed.add(e.getKey());
                    }
                }
            }
            for (IntegrationProvider.Field f : provider.fields()) {                                  // requeridos
                if (f.required() && (f.secret() ? !stored.containsKey(f.name()) : isBlank(cleanSettings.get(f.name())))) {
                    throw new Exceptions("error.integration.fieldRequired", HttpStatus.BAD_REQUEST, "en".equals(lang()) ? f.labelEn() : f.labelEs());
                }
            }
            Map<String, Object> before = new LinkedHashMap<>(c.getSettings());
            c.setSettings(cleanSettings);
            c.setSecretsEnc(stored.isEmpty() ? null : encrypt(stored));
            reset(c);
            repository.saveAndFlush(c);
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("provider", provider.name());
            diff.put("settings", Map.of("from", before, "to", cleanSettings));
            diff.put("credentialsUpdated", changed);                                                  // solo nombres, nunca valores
            audit.record(new AuditService.Command(MODULE, creating ? "CREATE" : "UPDATE", ENTITY, c.getId(), org, null, diff));
            return card(provider, c);
        });
    }

    /** Reemplaza credenciales (rotación). Vuelve a TESTING. */
    public Card rotate(AuthenticatedActor actor, IntegrationProvider provider, Map<String, String> secrets) {
        guard.requireOrgAdmin(actor);
        return tx.execute(st -> {
            UUID org = actor.organizationId();
            guard.assertOpen(org);
            IntegrationConfig c = repository.findByOrganizationIdAndProvider(org, provider)
                    .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
            Map<String, String> stored = readSecrets(c);
            List<String> changed = new ArrayList<>();
            if (secrets != null) {
                for (Map.Entry<String, String> e : secrets.entrySet()) {
                    IntegrationProvider.Field f = field(provider, e.getKey());
                    if (f == null || !f.secret()) {
                        throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, e.getKey());
                    }
                    if (e.getValue() != null && !e.getValue().isBlank()) {
                        stored.put(e.getKey(), e.getValue());
                        changed.add(e.getKey());
                    }
                }
            }
            if (changed.isEmpty()) {
                throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "credenciales");
            }
            c.setSecretsEnc(encrypt(stored));
            reset(c);
            repository.saveAndFlush(c);
            audit.record(new AuditService.Command(MODULE, "ROTATE", ENTITY, c.getId(), org, null,
                    Map.of("provider", provider.name(), "credentialsUpdated", changed)));
            return card(provider, c);
        });
    }

    /**
     * Prueba la conexión. La llamada de red va fuera de la transacción; el resultado (bueno o malo) se guarda siempre y,
     * si falló, se responde 422 con el motivo.
     */
    public Card test(AuthenticatedActor actor, IntegrationProvider provider) {
        guard.requireOrgAdmin(actor);
        UUID org = actor.organizationId();
        record Snapshot(UUID id, Map<String, Object> settings, Map<String, String> secrets) {
        }
        Snapshot snap = tx.execute(st -> {
            guard.assertOpen(org);
            IntegrationConfig c = repository.findByOrganizationIdAndProvider(org, provider)
                    .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
            return new Snapshot(c.getId(), new HashMap<>(c.getSettings()), readSecrets(c));
        });
        IntegrationTester tester = testers.orderedStream().filter(t -> t.supports(provider)).findFirst().orElse(null);
        if (tester == null) {
            throw new Exceptions("error.integration.testUnavailable", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        TestResult result;
        try {
            result = tester.test(snap.settings(), snap.secrets());
        } catch (RuntimeException e) {
            result = TestResult.fail("error inesperado");
        }
        final TestResult r = result;
        Card card = tx.execute(st -> {
            IntegrationConfig c = repository.findById(snap.id()).orElseThrow();
            c.setLastTestAt(clock.instant());
            c.setLastTestOk(r.ok());
            c.setLastError(r.ok() ? null : clip(scrub(r.error(), snap.secrets())));
            if (r.ok()) {
                c.setConsecutiveFailures(0);
                if (IntegrationConfig.ERROR.equals(c.getStatus())) {
                    c.setStatus(IntegrationConfig.TESTING);
                }
            } else if (IntegrationConfig.ACTIVE.equals(c.getStatus())) {
                failure(c);
            }
            repository.saveAndFlush(c);
            audit.record(new AuditService.Command(MODULE, "TEST", ENTITY, c.getId(), org, null,
                    Map.of("provider", provider.name(), "ok", r.ok())));
            return card(provider, c);
        });
        if (!r.ok()) {
            throw new Exceptions("error.integration.testFailed", HttpStatus.UNPROCESSABLE_ENTITY, clip(scrub(r.error(), snap.secrets())));
        }
        return card;
    }

    public Card activate(AuthenticatedActor actor, IntegrationProvider provider) {
        guard.requireOrgAdmin(actor);
        return tx.execute(st -> {
            UUID org = actor.organizationId();
            guard.assertOpen(org);
            IntegrationConfig c = repository.findByOrganizationIdAndProvider(org, provider)
                    .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
            if (IntegrationConfig.ACTIVE.equals(c.getStatus())) {
                throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, c.getStatus());
            }
            if (!testFresh(c)) {                                                                       // [V9]
                throw new Exceptions("error.integration.testRequired", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (provider.isEmail()) {                                                                  // [V11]
                String from = String.valueOf(c.getSettings().getOrDefault("fromEmail", ""));
                String domain = from.contains("@") ? from.substring(from.indexOf('@') + 1) : "";
                if (!domains.verified(domain, String.valueOf(c.getSettings().getOrDefault("dkimSelector", "")))) {
                    throw new Exceptions("error.integration.domainUnverified", HttpStatus.UNPROCESSABLE_ENTITY);
                }
            }
            repository.findByOrganizationIdAndKindAndStatus(org, c.getKind(), IntegrationConfig.ACTIVE)
                    .filter(other -> !other.getId().equals(c.getId()))
                    .ifPresent(other -> {
                        throw new Exceptions("error.integration.alreadyActive", HttpStatus.UNPROCESSABLE_ENTITY);
                    });
            c.setStatus(IntegrationConfig.ACTIVE);
            c.setConsecutiveFailures(0);
            repository.saveAndFlush(c);
            audit.record(new AuditService.Command(MODULE, "ACTIVATE", ENTITY, c.getId(), org, null, Map.of("provider", provider.name())));
            return card(provider, c);
        });
    }

    public Card disable(AuthenticatedActor actor, IntegrationProvider provider) {
        guard.requireOrgAdmin(actor);
        return tx.execute(st -> {
            UUID org = actor.organizationId();
            guard.assertOpen(org);
            IntegrationConfig c = repository.findByOrganizationIdAndProvider(org, provider)
                    .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
            if (!IntegrationConfig.ACTIVE.equals(c.getStatus()) && !IntegrationConfig.ERROR.equals(c.getStatus())) {
                throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, c.getStatus());
            }
            reset(c);
            repository.saveAndFlush(c);
            audit.record(new AuditService.Command(MODULE, "DISABLE", ENTITY, c.getId(), org, null, Map.of("provider", provider.name())));
            return card(provider, c);
        });
    }

    // ---------------------------------------------------------------- uso por otros módulos (M19, M15)

    /** Un módulo que usa la integración ACTIVE de un tipo informa un fallo; con 3 seguidos pasa a ERROR (y usa el proveedor de plataforma). */
    public void reportFailure(UUID orgId, String kind, String error) {
        tx.executeWithoutResult(st -> repository.findByOrganizationIdAndKindAndStatus(orgId, kind, IntegrationConfig.ACTIVE).ifPresent(c -> {
            c.setLastError(clip(error));
            failure(c);
            repository.saveAndFlush(c);
        }));
    }

    public void reportSuccess(UUID orgId, String kind) {
        tx.executeWithoutResult(st -> repository.findByOrganizationIdAndKindAndStatus(orgId, kind, IntegrationConfig.ACTIVE).ifPresent(c -> {
            if (c.getConsecutiveFailures() > 0) {
                c.setConsecutiveFailures(0);
                repository.saveAndFlush(c);
            }
        }));
    }

    /** Credenciales descifradas para quien envía (M19); nunca se exponen por la API. */
    public Optional<Map<String, String>> activeSecrets(UUID orgId, String kind) {
        return tx.execute(st -> repository.findByOrganizationIdAndKindAndStatus(orgId, kind, IntegrationConfig.ACTIVE).map(this::readSecrets));
    }

    // ---------------------------------------------------------------- internos

    private void failure(IntegrationConfig c) {
        c.setConsecutiveFailures(c.getConsecutiveFailures() + 1);
        if (c.getConsecutiveFailures() >= MAX_FAILURES) {
            c.setStatus(IntegrationConfig.ERROR);
            audit.record(new AuditService.Command(MODULE, "ERROR", ENTITY, c.getId(), c.getOrganizationId(), null,
                    Map.of("provider", c.getProvider().name(), "failures", c.getConsecutiveFailures())));
        }
    }

    private static void reset(IntegrationConfig c) {
        c.setStatus(IntegrationConfig.TESTING);
        c.setLastTestAt(null);
        c.setLastTestOk(null);
        c.setLastError(null);
        c.setConsecutiveFailures(0);
    }

    private boolean testFresh(IntegrationConfig c) {
        return Boolean.TRUE.equals(c.getLastTestOk()) && c.getLastTestAt() != null
                && c.getLastTestAt().plus(TEST_VALIDITY).isAfter(clock.instant());
    }

    private Card card(IntegrationProvider p, IntegrationConfig c) {
        if (c == null) {
            return new Card(p.name(), p.kind(), IntegrationConfig.UNCONFIGURED, false, p.fields(), Map.of(), Map.of(), null, null, null, false, false, null);
        }
        Map<String, String> secrets = readSecrets(c);
        Map<String, Boolean> configured = new LinkedHashMap<>();
        p.fields().stream().filter(IntegrationProvider.Field::secret).forEach(f -> configured.put(f.name(), secrets.containsKey(f.name())));
        boolean fresh = testFresh(c);
        return new Card(p.name(), p.kind(), c.getStatus(), true, p.fields(), c.getSettings(), configured, c.getLastTestAt(),
                c.getLastTestOk(), c.getLastError(), fresh, fresh && !IntegrationConfig.ACTIVE.equals(c.getStatus()), c.getVersion());
    }

    private Map<String, Object> cleanSettings(IntegrationProvider p, Map<String, Object> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> src = in == null ? Map.of() : in;
        for (IntegrationProvider.Field f : p.fields()) {
            if (f.secret() || !src.containsKey(f.name())) {
                continue;
            }
            Object v = src.get(f.name());
            String s = v == null ? "" : v.toString().trim();
            if (s.isEmpty()) {
                continue;
            }
            if (s.length() > 200) {
                throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, f.labelEs(), 200);
            }
            switch (f.type()) {
                case "number" -> {
                    if (!s.matches("\\d{1,5}") || Integer.parseInt(s) < 1 || Integer.parseInt(s) > 65535) {
                        throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, f.labelEs());
                    }
                    out.put(f.name(), Integer.parseInt(s));
                }
                case "email" -> {
                    if (!s.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                        throw new Exceptions("error.common.emailInvalid", HttpStatus.BAD_REQUEST);
                    }
                    out.put(f.name(), s.toLowerCase());
                }
                case "select" -> {
                    if (f.options() == null || !f.options().contains(s)) {
                        throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, f.labelEs());
                    }
                    out.put(f.name(), s);
                }
                default -> out.put(f.name(), s);
            }
        }
        return out;
    }

    private static IntegrationProvider.Field field(IntegrationProvider p, String name) {
        return p.fields().stream().filter(f -> f.name().equals(name)).findFirst().orElse(null);
    }

    private Map<String, String> readSecrets(IntegrationConfig c) {
        if (c.getSecretsEnc() == null) {
            return new HashMap<>();
        }
        try {
            return mapper.readValue(cipher.decrypt(c.getSecretsEnc()), new TypeReference<HashMap<String, String>>() {
            });
        } catch (Exception e) {
            log.error("No se pudieron leer las credenciales de la integración {}", c.getId());
            return new HashMap<>();
        }
    }

    private String encrypt(Map<String, String> secrets) {
        try {
            return cipher.encrypt(mapper.writeValueAsString(secrets));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudieron cifrar las credenciales", e);
        }
    }

    /** Quita de un mensaje de error cualquier valor de secreto que haya podido colarse. */
    private static String scrub(String error, Map<String, String> secrets) {
        String out = error == null ? "" : error;
        for (String v : secrets.values()) {
            if (v != null && v.length() >= 4) {
                out = out.replace(v, "***");
            }
        }
        return out;
    }

    private static String clip(String s) {
        return s == null ? null : (s.length() > 480 ? s.substring(0, 480) : s);
    }

    private static boolean isBlank(Object o) {
        return o == null || o.toString().isBlank();
    }

    private static String lang() {
        return org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage();
    }
}
