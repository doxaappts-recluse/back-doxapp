package pe.dcs.app.features.announcement.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.announcement.domain.PlatformAnnouncement;
import pe.dcs.app.features.announcement.domain.PlatformAnnouncementRepository;
import pe.dcs.app.features.announcement.dto.*;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * M22 · anuncios de plataforma. Flujo: borrador → (audiencia, fechas) → publicar → visible entre "desde" y "hasta" → o cancelar.
 * V8: hasta &gt; desde y audiencia no vacía. Un mantenimiento se publica con al menos 24 h de aviso (un incidente, sin aviso).
 * Solo SYSTEM_ADMIN escribe; SYSTEM_SUPPORT consulta.
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private static final String MODULE = "PLATFORM_ANNOUNCEMENTS";
    private static final Set<String> SEVERITIES = Set.of("INFO", "MAINTENANCE", "INCIDENT");
    private static final Set<String> AUDIENCES = Set.of("ALL", "ORGS", "PLANS");
    private static final Duration MAINTENANCE_NOTICE = Duration.ofHours(24);

    private final PlatformAnnouncementRepository repo;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    // ------------------------------------------------------------------ plataforma

    @Transactional(readOnly = true)
    public PageResponse<AnnouncementResponse> search(AuthenticatedActor actor, AnnouncementSearchRequest req) {
        requireStaff(actor);
        AnnouncementSearchRequest.Filters f = req == null || req.filters() == null
                ? new AnnouncementSearchRequest.Filters(null, null, null, null) : req.filters();
        Instant now = clock.instant();
        Specification<PlatformAnnouncement> spec = (r, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.q() != null && !f.q().isBlank()) {
                ps.add(cb.like(cb.lower(r.get("title")), "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (f.status() != null && !f.status().isBlank()) {
                ps.add(cb.equal(r.get("status"), f.status().trim().toUpperCase(Locale.ROOT)));
            }
            if (f.severity() != null && !f.severity().isBlank()) {
                ps.add(cb.equal(r.get("severity"), f.severity().trim().toUpperCase(Locale.ROOT)));
            }
            if (f.phase() != null && !f.phase().isBlank()) {
                ps.add(cb.equal(r.get("status"), "PUBLISHED"));
                switch (f.phase().trim().toUpperCase(Locale.ROOT)) {
                    case "SCHEDULED" -> ps.add(cb.greaterThan(r.<Instant>get("startsAt"), now));
                    case "ACTIVE" -> {
                        ps.add(cb.lessThanOrEqualTo(r.<Instant>get("startsAt"), now));
                        ps.add(cb.greaterThan(r.<Instant>get("endsAt"), now));
                    }
                    case "ENDED" -> ps.add(cb.lessThanOrEqualTo(r.<Instant>get("endsAt"), now));
                    default -> throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "phase");
                }
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        PaginationRequest p = req == null || req.pagination() == null ? new PaginationRequest() : req.pagination();
        Page<PlatformAnnouncement> page = repo.findAll(spec, PageRequest.of(Math.max(p.getPage(), 0), Math.min(Math.max(p.getSize(), 1), 100),
                Sort.by(Sort.Direction.DESC, "startsAt").and(Sort.by(Sort.Direction.DESC, "createdAt"))));
        return new PageResponse<>(page.getContent().stream().map(a -> toResponse(a, now)).toList(),
                new PaginationResponse((int) page.getTotalElements(), page.getTotalPages(), page.getSize(), page.getNumber()));
    }

    @Transactional(readOnly = true)
    public AnnouncementResponse get(AuthenticatedActor actor, UUID id) {
        requireStaff(actor);
        return toResponse(load(id), clock.instant());
    }

    @Transactional
    public AnnouncementResponse create(AuthenticatedActor actor, AnnouncementRequest req) {
        requireAdmin(actor);
        PlatformAnnouncement a = new PlatformAnnouncement();
        apply(a, req);
        a = repo.save(a);
        audit.record(new AuditService.Command(MODULE, "CREATE", "PlatformAnnouncement", a.getId(), null, null, snapshot(a)));
        return toResponse(a, clock.instant());
    }

    @Transactional
    public AnnouncementResponse update(AuthenticatedActor actor, UUID id, AnnouncementRequest req) {
        requireAdmin(actor);
        PlatformAnnouncement a = load(id);
        if (!"DRAFT".equals(a.getStatus())) {
            throw new Exceptions("error.announcement.notEditable", HttpStatus.CONFLICT);
        }
        checkVersion(a, req == null ? null : req.version());
        apply(a, req);
        a = repo.save(a);
        audit.record(new AuditService.Command(MODULE, "UPDATE", "PlatformAnnouncement", a.getId(), null, null, snapshot(a)));
        return toResponse(a, clock.instant());
    }

    @Transactional
    public AnnouncementResponse publish(AuthenticatedActor actor, UUID id) {
        requireAdmin(actor);
        PlatformAnnouncement a = load(id);
        if (!"DRAFT".equals(a.getStatus())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, a.getStatus());
        }
        Instant now = clock.instant();
        if (!a.getEndsAt().isAfter(now)) {
            throw new Exceptions("error.announcement.expired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if ("MAINTENANCE".equals(a.getSeverity()) && a.getStartsAt().isBefore(now.plus(MAINTENANCE_NOTICE))) {
            throw new Exceptions("error.announcement.notice", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        a.setStatus("PUBLISHED");
        a.setPublishedAt(now);
        a = repo.save(a);
        audit.record(new AuditService.Command(MODULE, "PUBLISH", "PlatformAnnouncement", a.getId(), null, null,
                Map.of("severity", a.getSeverity(), "audience", a.getAudienceType())));
        return toResponse(a, now);
    }

    @Transactional
    public AnnouncementResponse cancel(AuthenticatedActor actor, UUID id) {
        requireAdmin(actor);
        PlatformAnnouncement a = load(id);
        if (!"PUBLISHED".equals(a.getStatus())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, a.getStatus());
        }
        a.setStatus("CANCELLED");
        a = repo.save(a);
        audit.record(new AuditService.Command(MODULE, "CANCEL", "PlatformAnnouncement", a.getId(), null, null, Map.of("title", a.getTitle())));
        return toResponse(a, clock.instant());
    }

    @Transactional
    public void delete(AuthenticatedActor actor, UUID id) {
        requireAdmin(actor);
        PlatformAnnouncement a = load(id);
        if (!"DRAFT".equals(a.getStatus())) {
            throw new Exceptions("error.announcement.notEditable", HttpStatus.CONFLICT);
        }
        repo.delete(a);
        audit.record(new AuditService.Command(MODULE, "DELETE", "PlatformAnnouncement", id, null, null, Map.of("title", a.getTitle())));
    }

    // ------------------------------------------------------------------ organización y portal

    /**
     * Anuncios vigentes para quien consulta. Organización: los publicados, dentro de fechas y dirigidos a su organización
     * (todas, una lista de organizaciones o el plan de su contrato vigente). Portal (MEMBER): solo INCIDENT marcados como visibles.
     */
    @Transactional(readOnly = true)
    public List<ActiveAnnouncement> active(AuthenticatedActor actor) {
        if (actor.isStaff() || actor.organizationId() == null) {
            return List.of();
        }
        UUID org = actor.organizationId();
        Instant now = clock.instant();
        UUID planId = null;
        List<UUID> plans = jdbc.queryForList("select plan_id from contract where organization_id = ? and status = 'ACTIVE' and plan_id is not null limit 1", UUID.class, org);
        if (!plans.isEmpty()) {
            planId = plans.get(0);
        }
        UUID plan = planId;
        Specification<PlatformAnnouncement> spec = (r, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(r.get("status"), "PUBLISHED"));
            ps.add(cb.lessThanOrEqualTo(r.<Instant>get("startsAt"), now));
            ps.add(cb.greaterThan(r.<Instant>get("endsAt"), now));
            if (actor.role() == RoleType.MEMBER) {
                ps.add(cb.equal(r.get("severity"), "INCIDENT"));
                ps.add(cb.isTrue(r.get("portalVisible")));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return repo.findAll(spec, Sort.by(Sort.Direction.DESC, "startsAt")).stream()
                .filter(a -> matches(a, org, plan))
                .map(a -> new ActiveAnnouncement(a.getId(), a.getTitle(), a.getBody(), a.getSeverity(), a.isDismissible(), a.getStartsAt(), a.getEndsAt()))
                .toList();
    }

    private static boolean matches(PlatformAnnouncement a, UUID org, UUID plan) {
        List<UUID> ids = Arrays.asList(a.getAudienceIds());
        return switch (a.getAudienceType()) {
            case "ALL" -> true;
            case "ORGS" -> ids.contains(org);
            case "PLANS" -> plan != null && ids.contains(plan);
            default -> false;
        };
    }

    // ------------------------------------------------------------------ validación

    private void apply(PlatformAnnouncement a, AnnouncementRequest req) {
        if (req == null) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "request");
        }
        String title = req.title() == null ? "" : req.title().trim();
        if (title.length() < 3 || title.length() > 120) {
            throw new Exceptions("error.announcement.titleLength", HttpStatus.BAD_REQUEST);
        }
        String body = req.body() == null ? "" : req.body().trim();
        if (body.isEmpty() || body.length() > 2000) {
            throw new Exceptions("error.announcement.bodyLength", HttpStatus.BAD_REQUEST);
        }
        String severity = req.severity() == null ? "" : req.severity().trim().toUpperCase(Locale.ROOT);
        if (!SEVERITIES.contains(severity)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "severity");
        }
        String audience = req.audienceType() == null ? "ALL" : req.audienceType().trim().toUpperCase(Locale.ROOT);
        if (!AUDIENCES.contains(audience)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "audienceType");
        }
        List<UUID> ids = req.audienceIds() == null ? List.of() : req.audienceIds().stream().filter(Objects::nonNull).distinct().toList();
        if (!"ALL".equals(audience)) {
            if (ids.isEmpty()) {
                throw new Exceptions("error.announcement.audience", HttpStatus.BAD_REQUEST);
            }
            String table = "ORGS".equals(audience) ? "organization" : "plan";
            String in = ids.stream().map(i -> "'" + i + "'").collect(Collectors.joining(","));
            Integer found = jdbc.queryForObject("select count(*) from " + table + " where id in (" + in + ")", Integer.class);
            if (found == null || found != ids.size()) {
                throw new Exceptions("error.announcement.audienceUnknown", HttpStatus.BAD_REQUEST);
            }
        } else {
            ids = List.of();
        }
        if (req.startsAt() == null || req.endsAt() == null || !req.endsAt().isAfter(req.startsAt())) {
            throw new Exceptions("error.announcement.range", HttpStatus.BAD_REQUEST);
        }
        a.setTitle(title);
        a.setBody(body);
        a.setSeverity(severity);
        a.setAudienceType(audience);
        a.setAudienceIds(ids.toArray(new UUID[0]));
        a.setStartsAt(req.startsAt());
        a.setEndsAt(req.endsAt());
        a.setDismissible(req.dismissible() == null || req.dismissible());
        a.setPortalVisible("INCIDENT".equals(severity) && Boolean.TRUE.equals(req.portalVisible()));
    }

    private void checkVersion(PlatformAnnouncement a, Long version) {
        if (version != null && !version.equals(a.getVersion())) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
    }

    private PlatformAnnouncement load(UUID id) {
        return repo.findById(id).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private void requireStaff(AuthenticatedActor actor) {
        if (!actor.isStaff()) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
    }

    private void requireAdmin(AuthenticatedActor actor) {
        if (actor.role() != RoleType.SYSTEM_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
    }

    private Map<String, Object> snapshot(PlatformAnnouncement a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", a.getTitle());
        m.put("severity", a.getSeverity());
        m.put("audience", a.getAudienceType());
        m.put("audienceCount", a.getAudienceIds().length);
        m.put("startsAt", a.getStartsAt().toString());
        m.put("endsAt", a.getEndsAt().toString());
        return m;
    }

    private AnnouncementResponse toResponse(PlatformAnnouncement a, Instant now) {
        List<UUID> ids = Arrays.asList(a.getAudienceIds());
        List<String> labels = new ArrayList<>();
        if (!ids.isEmpty()) {
            String table = "ORGS".equals(a.getAudienceType()) ? "organization" : "plan";
            String in = ids.stream().map(i -> "'" + i + "'").collect(Collectors.joining(","));
            labels = jdbc.queryForList("select name from " + table + " where id in (" + in + ") order by name", String.class);
        }
        String phase = null;
        if ("PUBLISHED".equals(a.getStatus())) {
            phase = a.getStartsAt().isAfter(now) ? "SCHEDULED" : a.getEndsAt().isAfter(now) ? "ACTIVE" : "ENDED";
        }
        return new AnnouncementResponse(a.getId(), a.getTitle(), a.getBody(), a.getSeverity(), a.getAudienceType(), ids, labels,
                a.getStartsAt(), a.getEndsAt(), a.isDismissible(), a.isPortalVisible(), a.getStatus(), phase, a.getPublishedAt(),
                a.getCreatedAt(), a.getVersion());
    }
}
