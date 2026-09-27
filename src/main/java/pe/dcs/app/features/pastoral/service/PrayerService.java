package pe.dcs.app.features.pastoral.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.pastoral.dto.PastoralDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M12 · Peticiones de oración: privadas, de líderes o públicas (muro de la congregación, solo tras moderación) [V10]-[V14].
 * Alcance: organización + sedes visibles; ORG_USER solo ve las suyas y las públicas ya aprobadas.
 */
@Service
@RequiredArgsConstructor
public class PrayerService {

    private static final String MODULE = "PRAYER";
    private static final String ENTITY = "PrayerRequest";
    private static final Set<String> VISIBILITY = Set.of("PRIVATE", "LEADERS", "CONGREGATION");
    private static final int MIN_LEN = 5;
    private static final int MAX_LEN = 1000;

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;

    record Row(UUID id, UUID orgId, UUID branchId, UUID requestedBy, UUID forPersonId, String text, String category, String visibility,
               boolean anonymous, String moderation, String status, Instant answeredAt, String testimony, Instant createdAt, long version) {
    }

    @Transactional(readOnly = true)
    public PageResponse<PastoralDtos.PrayerSummary> search(AccessScope scope, PastoralDtos.PrayerSearch req) {
        PastoralDtos.PrayerSearch.PrayerFilters f = req == null || req.filters() == null
                ? new PastoralDtos.PrayerSearch.PrayerFilters(null, null, null, null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(visible(scope, ps, "r"));
        if (hasText(f.status())) {
            w.append(" and r.status = :status");
            ps.addValue("status", f.status().trim().toUpperCase());
        }
        if (hasText(f.moderation())) {
            w.append(" and r.moderation = :mod");
            ps.addValue("mod", f.moderation().trim().toUpperCase());
        }
        if (hasText(f.visibility())) {
            w.append(" and r.visibility = :vis");
            ps.addValue("vis", f.visibility().trim().toUpperCase());
        }
        if (hasText(f.category())) {
            w.append(" and r.category = :cat");
            ps.addValue("cat", f.category().trim().toUpperCase());
        }
        if (f.branchId() != null) {
            w.append(" and r.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (Boolean.TRUE.equals(f.mine())) {
            w.append(" and r.requested_by = :me");
            ps.addValue("me", scope.personId());
        }
        String from = " from prayer_request r left join person rp on rp.id = r.requested_by left join person fp on fp.id = r.for_person_id where ";
        Long total = jdbc.queryForObject("select count(*)" + from + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<PastoralDtos.PrayerSummary> rows = jdbc.query(SUMMARY + from + w + " order by r.created_at desc, r.id limit :lim offset :off", ps, this::summary);
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public PastoralDtos.PrayerResponse get(AccessScope scope, UUID id) {
        Row r = load(scope, id);
        return build(r);
    }

    /** Solo el muro (CONGREGATION + APPROVED) de la organización; sin autor si {@code anonymous} [V12]. */
    @Transactional(readOnly = true)
    public List<PastoralDtos.WallItem> wall(AccessScope scope, int limit) {
        int lim = Math.max(1, Math.min(limit, 100));
        return jdbc.query("select r.id, r.anonymous, rp.first_name, rp.last_name, r.text, r.category, r.status, r.created_at,"
                        + " (select count(*) from prayer_support s where s.request_id = r.id) as n_support"
                        + " from prayer_request r left join person rp on rp.id = r.requested_by"
                        + " where r.organization_id = :o and r.visibility = 'CONGREGATION' and r.moderation = 'APPROVED'"
                        + " order by r.created_at desc limit :lim",
                new MapSqlParameterSource("o", scope.organizationId()).addValue("lim", lim), (rs, i) -> {
                    boolean anon = rs.getBoolean("anonymous");
                    String fn = rs.getString("first_name");
                    return new PastoralDtos.WallItem((UUID) rs.getObject("id"), anon || fn == null ? null : (fn + " " + rs.getString("last_name")).trim(),
                            rs.getString("text"), rs.getString("category"), rs.getLong("n_support"), "ANSWERED".equals(rs.getString("status")),
                            rs.getTimestamp("created_at").toInstant());
                });
    }

    @Transactional
    public PastoralDtos.PrayerResponse create(AuthenticatedActor actor, AccessScope scope, PastoralDtos.PrayerCreateRequest r) {
        if (r == null || !hasText(r.text())) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "petición");
        }
        String text = r.text().trim();
        if (text.length() < MIN_LEN || text.length() > MAX_LEN) {                                                        // [V10]
            throw new Exceptions("error.prayer.textLength", HttpStatus.BAD_REQUEST, MIN_LEN, MAX_LEN);
        }
        String category = catalogCode(scope.organizationId(), r.category());
        String visibility = hasText(r.visibility()) ? r.visibility().trim().toUpperCase() : "PRIVATE";
        if (!VISIBILITY.contains(visibility)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "visibilidad");
        }
        if (r.forPersonId() != null) {
            assertPersonInOrg(scope.organizationId(), r.forPersonId());
        }
        boolean anonymous = Boolean.TRUE.equals(r.anonymous());
        String moderation = "CONGREGATION".equals(visibility) ? "PENDING" : "NA";                                        // [V11]
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(clock.instant());
        UUID branchId = actor.activeBranchId();
        jdbc.update("insert into prayer_request (id, organization_id, branch_id, requested_by, for_person_id, text, category, visibility,"
                        + " anonymous, moderation, status, created_at, updated_at)"
                        + " values (:id, :o, :b, :rb, :fp, :t, :c, :v, :a, :m, 'OPEN', :at, :at)",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", branchId).addValue("rb", scope.personId())
                        .addValue("fp", r.forPersonId()).addValue("t", text).addValue("c", category).addValue("v", visibility).addValue("a", anonymous)
                        .addValue("m", moderation).addValue("at", now));
        audit.record(new AuditService.Command(MODULE, "CREATE", ENTITY, id, scope.organizationId(), branchId, Map.of("visibility", visibility)));
        return build(load(scope, id));
    }

    /** Aprueba o rechaza una petición pública. Quien la escribió no puede moderar la suya [V14]. */
    @Transactional
    public PastoralDtos.PrayerResponse moderate(AuthenticatedActor actor, AccessScope scope, UUID id, PastoralDtos.ModerateRequest r) {
        Row row = load(scope, id);
        if (!"PENDING".equals(row.moderation())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, row.moderation());
        }
        if (row.requestedBy() != null && row.requestedBy().equals(scope.personId())) {                                  // [V14]
            throw new Exceptions("error.prayer.selfModeration", HttpStatus.FORBIDDEN);
        }
        String decision = r == null ? null : r.decision();
        if (!"APPROVE".equalsIgnoreCase(decision) && !"REJECT".equalsIgnoreCase(decision)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "decisión");
        }
        boolean approve = "APPROVE".equalsIgnoreCase(decision);
        if (!approve && (r == null || !hasText(r.reason()))) {
            throw new Exceptions("error.prayer.rejectReasonRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("update prayer_request set moderation = :m, moderated_by = :by, moderated_at = :at, reject_reason = :r, updated_at = :at,"
                        + " version = version + 1 where id = :id",
                new MapSqlParameterSource("m", approve ? "APPROVED" : "REJECTED").addValue("by", scope.personId()).addValue("at", now)
                        .addValue("r", approve ? null : PastoralCaseService.clean(r.reason(), 300)).addValue("id", id));
        audit.record(new AuditService.Command(MODULE, "MODERATE", ENTITY, id, row.orgId(), row.branchId(), Map.of("decision", decision)));
        return build(load(scope, id));
    }

    /** "Oré por esto": un apoyo por persona (índice único); repetir no falla, simplemente no suma de nuevo. */
    @Transactional
    public long support(AuthenticatedActor actor, AccessScope scope, UUID id) {
        Row row = load(scope, id);
        jdbc.update("insert into prayer_support (id, request_id, person_id, at) values (:id, :r, :p, :at) on conflict do nothing",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("r", id).addValue("p", scope.personId()).addValue("at", Timestamp.from(clock.instant())));
        Long n = jdbc.queryForObject("select count(*) from prayer_support where request_id = :id", new MapSqlParameterSource("id", id), Long.class);
        audit.record(new AuditService.Command(MODULE, "SUPPORT", ENTITY, id, row.orgId(), row.branchId(), Map.of()));
        return n == null ? 0 : n;
    }

    /** Marca la petición como respondida; exige la fecha [V13]. */
    @Transactional
    public PastoralDtos.PrayerResponse answer(AuthenticatedActor actor, AccessScope scope, UUID id, PastoralDtos.AnswerRequest r) {
        Row row = load(scope, id);
        if ("ANSWERED".equals(row.status()) || "CLOSED".equals(row.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, row.status());
        }
        LocalDate when = r == null ? null : r.answeredAt();
        if (when == null) {                                                                                              // [V13]
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "fecha");
        }
        if (when.isAfter(LocalDate.now(clock))) {
            throw new Exceptions("error.common.futureDate", HttpStatus.BAD_REQUEST);
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("update prayer_request set status = 'ANSWERED', answered_at = :aa, testimony = :t, updated_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("aa", Timestamp.valueOf(when.atStartOfDay())).addValue("t", PastoralCaseService.clean(r.testimony(), 1000))
                        .addValue("at", now).addValue("id", id));
        audit.record(new AuditService.Command(MODULE, "ANSWER", ENTITY, id, row.orgId(), row.branchId(), Map.of()));
        return build(load(scope, id));
    }

    // ---------------------------------------------------------------- alcance y utilidades

    private static String visible(AccessScope scope, MapSqlParameterSource ps, String a) {
        StringBuilder sb = new StringBuilder(a + ".organization_id = :org");
        ps.addValue("org", scope.organizationId());
        if (scope.role() == RoleType.ORG_USER) {
            ps.addValue("me", scope.personId() == null ? new UUID(0, 0) : scope.personId());
            sb.append(" and (").append(a).append(".requested_by = :me or (").append(a).append(".visibility = 'CONGREGATION' and ")
                    .append(a).append(".moderation = 'APPROVED'))");
        }
        return sb.toString();
    }

    private Row load(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String vis = visible(scope, ps, "r");
        return jdbc.query("select r.* from prayer_request r where r.id = :id and " + vis, ps, (rs, i) -> row(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row((UUID) rs.getObject("id"), (UUID) rs.getObject("organization_id"), (UUID) rs.getObject("branch_id"), (UUID) rs.getObject("requested_by"),
                (UUID) rs.getObject("for_person_id"), rs.getString("text"), rs.getString("category"), rs.getString("visibility"), rs.getBoolean("anonymous"),
                rs.getString("moderation"), rs.getString("status"), instant(rs.getTimestamp("answered_at")), rs.getString("testimony"),
                instant(rs.getTimestamp("created_at")), rs.getLong("version"));
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static final String SUMMARY = "select r.id, r.requested_by, rp.first_name as rf, rp.last_name as rl, r.for_person_id, fp.first_name as ff,"
            + " fp.last_name as fl, r.text, r.category, r.visibility, r.anonymous, r.moderation, r.status, r.created_at,"
            + " (select count(*) from prayer_support s where s.request_id = r.id) as n_support";

    private PastoralDtos.PrayerSummary summary(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        String rf = rs.getString("rf");
        String ff = rs.getString("ff");
        return new PastoralDtos.PrayerSummary((UUID) rs.getObject("id"), (UUID) rs.getObject("requested_by"),
                rf == null ? null : (rf + " " + rs.getString("rl")).trim(), (UUID) rs.getObject("for_person_id"),
                ff == null ? null : (ff + " " + rs.getString("fl")).trim(), rs.getString("text"), rs.getString("category"), rs.getString("visibility"),
                rs.getBoolean("anonymous"), rs.getString("moderation"), rs.getString("status"), rs.getLong("n_support"), rs.getTimestamp("created_at").toInstant());
    }

    private PastoralDtos.PrayerResponse build(Row r) {
        PastoralDtos.PrayerSummary s = jdbc.query(SUMMARY + " from prayer_request r left join person rp on rp.id = r.requested_by"
                + " left join person fp on fp.id = r.for_person_id where r.id = :id", new MapSqlParameterSource("id", r.id()), this::summary).get(0);
        return new PastoralDtos.PrayerResponse(s, null, r.answeredAt(), r.testimony(), r.version());
    }

    private String catalogCode(UUID orgId, String raw) {
        if (!hasText(raw)) {
            return null;
        }
        String code = raw.trim().toUpperCase();
        Integer n = jdbc.queryForObject("select count(*) from catalog_item where type = 'PRAYER_CATEGORY' and code = :c and active"
                        + " and (organization_id is null or organization_id = :o)",
                new MapSqlParameterSource("c", code).addValue("o", orgId), Integer.class);
        if (n == null || n == 0) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "categoría");
        }
        return code;
    }

    private void assertPersonInOrg(UUID orgId, UUID personId) {
        Integer n = jdbc.queryForObject("select count(*) from person where id = :id and organization_id = :o",
                new MapSqlParameterSource("id", personId).addValue("o", orgId), Integer.class);
        if (n == null || n == 0) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
