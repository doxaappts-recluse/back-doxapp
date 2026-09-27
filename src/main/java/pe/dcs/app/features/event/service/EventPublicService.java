package pe.dcs.app.features.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.event.dto.EventDtos;
import pe.dcs.app.features.person.service.ConsentService;
import pe.dcs.app.features.person.service.PersonLookupService;
import pe.dcs.app.features.person.service.PersonService;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.ContractGate;
import pe.dcs.app.util.Exceptions;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * M14 · Formulario público de inscripción (sin sesión), mismo patrón que M07: honeypot, límite por IP y consentimiento
 * obligatorios [V15]; dedupe por teléfono/correo, crea una Person borrador si no encuentra una activa. Categoría fija GUEST
 * (sin tutor de hogar ni verificación de documento): el registro completo de un menor por su tutor queda para el personal
 * o para el portal (M24).
 */
@Service
@RequiredArgsConstructor
public class EventPublicService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ContractGate contracts;
    private final EventService events;
    private final EventRegistrationService registrations;
    private final PersonLookupService lookup;
    private final PersonService persons;
    private final ConsentService consents;
    private final EventPublicRateLimiter limiter;

    private record Org(UUID id, String name) {
    }

    @Transactional(readOnly = true)
    public EventDtos.PublicEventView config(String slug, UUID eventId) {
        Org org = org(slug);
        AccessScope scope = new AccessScope(org.id(), true, Set.of(), null, null, null);
        EventService.ERow e = events.load(scope, eventId);
        if (!"PUBLISHED".equals(e.status()) || !isPublic(eventId)) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        EventDtos.EventDetail d = events.get(scope, eventId);
        List<EventDtos.PriceTier> tiers = d.priceTiers().stream().filter(t -> Set.of("GUEST", "VISITOR").contains(t.category())).toList();
        boolean full = d.summary().capacity() != null && d.summary().registeredCount() >= d.summary().capacity() && !d.waitlistEnabled();
        return new EventDtos.PublicEventView(eventId, org.name(), d.summary().name(), d.description(), d.summary().startAt(), d.summary().endAt(),
                d.summary().location(), d.summary().onlineUrl(), d.bannerUrl(), tiers, d.questions(), full);
    }

    private boolean isPublic(UUID eventId) {
        List<Boolean> p = jdbc.query("select is_public from org_event where id = :id", new MapSqlParameterSource("id", eventId), (rs, i) -> rs.getBoolean(1));
        return !p.isEmpty() && p.get(0);
    }

    /** Sede principal (is_main) de la organización; si por alguna razón no hay una marcada, cae a la primera sede activa. */
    private UUID mainBranchOf(UUID orgId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", orgId);
        List<UUID> main = jdbc.query("select id from branch where organization_id = :o and is_main and status = 'ACTIVE'", ps, (rs, i) -> (UUID) rs.getObject(1));
        if (!main.isEmpty()) {
            return main.get(0);
        }
        List<UUID> any = jdbc.query("select id from branch where organization_id = :o and status = 'ACTIVE' order by created_at limit 1", ps, (rs, i) -> (UUID) rs.getObject(1));
        return any.isEmpty() ? null : any.get(0);
    }

    /** Siempre responde igual, haya o no inscrito realmente (señuelo, evento lleno sin lista de espera se informa como fallo genérico). */
    @Transactional
    public void register(String slug, UUID eventId, String ip, EventDtos.PublicRegisterRequest r) {
        if (!limiter.tryAcquire(ip == null ? "unknown" : ip)) {
            throw new Exceptions("error.event.rateLimited", HttpStatus.TOO_MANY_REQUESTS);                                 // [V18]
        }
        Org org = org(slug);
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        if (r.website() != null && !r.website().isBlank()) {
            return;                                                                                                       // honeypot
        }
        if (!Boolean.TRUE.equals(r.consent())) {
            throw new Exceptions("error.event.consentRequired", HttpStatus.BAD_REQUEST);                                   // [V15]
        }
        AccessScope scope = new AccessScope(org.id(), true, Set.of(), null, null, null);
        EventService.ERow e = events.load(scope, eventId);
        if (!"PUBLISHED".equals(e.status()) || !isPublic(eventId)) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        String phone = PersonService.normalizePhone(r.phone());
        if (phone == null && (r.email() == null || r.email().isBlank())) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "teléfono o correo");
        }
        UUID personId = lookup.findMatches(scope, null, null, phone, r.email()).people().stream()
                .filter(p -> "ACTIVE".equals(p.status())).map(PersonLookupService.PersonMin::id).findFirst().orElse(null);
        if (personId == null) {
            // Toda Person requiere una sede principal (person_branch); un evento de alcance ORGANIZATION no tiene sede propia,
            // así que un inscrito nuevo se registra en la sede principal (is_main) de la organización.
            UUID homeBranchId = e.branchId() != null ? e.branchId() : mainBranchOf(org.id());
            personId = persons.registerBasic(org.id(), homeBranchId, r.firstName(), r.lastName(), r.phone(), r.email(), null, null, null, "PUBLIC_FORM");
        }
        consents.grantDefaults(org.id(), personId, "PUBLIC_FORM", null);
        EventDtos.RegisterRequest req = new EventDtos.RegisterRequest(personId, "GUEST", 0, r.answers(), null, null);
        registrations.registerCore(scope, eventId, req, "PUBLIC", null);
    }

    private Org org(String rawSlug) {
        String slug = rawSlug == null ? "" : rawSlug.trim().toLowerCase();
        List<Org> l = jdbc.query("select id, name from organization where slug = :s and status = 'ACTIVE'", new MapSqlParameterSource("s", slug),
                (rs, i) -> new Org((UUID) rs.getObject(1), rs.getString(2)));
        if (l.isEmpty() || !contracts.enabled(l.get(0).id(), "EVENTS")) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return l.get(0);
    }
}
