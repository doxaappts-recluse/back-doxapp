package pe.dcs.app.features.attendance.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.attendance.dto.AttendanceDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.util.Exceptions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * M09 · Códigos QR firmados (HMAC-SHA256, sin guardar el código). Dos tipos:
 * P = QR personal (persona + sede, vigencia corta, un solo uso [V8]) · S = QR del culto para el autoservicio (sesión, [V9]).
 * El portal del miembro (M24) emitirá el QR personal con {@link #issuePersonal}.
 */
@Service
public class AttendanceQrService {

    private static final String PREFIX = "DXQ1";
    private static final Duration SELF_BEFORE = Duration.ofMinutes(30);
    private static final Duration SELF_AFTER = Duration.ofMinutes(60);

    private final NamedParameterJdbcTemplate jdbc;
    private final AttendanceService attendance;
    private final AttendanceRulesService rules;
    private final AttendanceSupport support;
    private final Clock clock;
    private final byte[] key;

    public AttendanceQrService(NamedParameterJdbcTemplate jdbc, AttendanceService attendance, AttendanceRulesService rules, AttendanceSupport support, Clock clock,
                               @Value("${app.jwt.secret:}") String secret) {
        this.jdbc = jdbc;
        this.attendance = attendance;
        this.rules = rules;
        this.support = support;
        this.clock = clock;
        try {
            this.key = MessageDigest.getInstance("SHA-256").digest(("attendance-qr:" + secret).getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Token(String type, UUID orgId, UUID branchId, UUID subject, Instant exp, String jti) {
    }

    // ---------------------------------------------------------------- emisión

    /** QR personal de la persona para una sede (vigencia = reglas de la organización). */
    @Transactional(readOnly = true)
    public AttendanceDtos.QrToken issuePersonal(UUID orgId, UUID personId, UUID branchId) {
        Integer n = jdbc.queryForObject("select count(*) from person where id = :p and organization_id = :o and status = 'ACTIVE' and anonymized_at is null",
                new MapSqlParameterSource("p", personId).addValue("o", orgId), Integer.class);
        if (n == null || n == 0) {
            throw new Exceptions("error.attendance.personInactive", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Integer b = jdbc.queryForObject("select count(*) from branch where id = :b and organization_id = :o and status = 'ACTIVE'",
                new MapSqlParameterSource("b", branchId).addValue("o", orgId), Integer.class);
        if (b == null || b == 0) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        int ttl = rules.get(orgId).qrTtlSeconds();
        Instant exp = clock.instant().plusSeconds(ttl);
        return new AttendanceDtos.QrToken(sign("P", orgId, branchId, personId, exp, UUID.randomUUID().toString().replace("-", "").substring(0, 20)), exp, ttl);
    }

    /** El QR personal de quien está autenticado (sede pedida o la de trabajo). */
    @Transactional(readOnly = true)
    public AttendanceDtos.QrToken issueMine(AuthenticatedActor actor, UUID branchId) {
        UUID branch = branchId != null ? branchId : actor.activeBranchId();
        if (branch == null) {
            List<UUID> l = jdbc.queryForList("select primary_branch_id from person where id = :p", new MapSqlParameterSource("p", actor.ownerId()), UUID.class);
            branch = l.isEmpty() ? null : l.get(0);
        }
        if (branch == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
        }
        return issuePersonal(actor.organizationId(), actor.ownerId(), branch);
    }

    /** QR del culto para el autoservicio: solo si el culto lo activa [V9]. */
    @Transactional(readOnly = true)
    public AttendanceDtos.QrToken sessionQr(AccessScope scope, UUID sessionId) {
        AttendanceService.Row s = attendance.load(scope, sessionId);
        if (!s.selfCheckin()) {
            throw new Exceptions("error.attendance.selfNotAllowed", HttpStatus.FORBIDDEN);
        }
        Instant exp = s.endsAt().plus(SELF_AFTER);
        return new AttendanceDtos.QrToken(sign("S", s.orgId(), s.branchId(), s.id(), exp, "s"), exp, (int) Duration.between(clock.instant(), exp).toSeconds());
    }

    // ---------------------------------------------------------------- lectura

    /** El ujier escanea el QR personal de alguien: [V8] vigente, de la sede de la sesión y de un solo uso. */
    @Transactional
    public AttendanceDtos.RecordResult scan(AuthenticatedActor actor, AccessScope scope, AttendanceDtos.QrScanRequest req) {
        if (req == null || req.sessionId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sesión");
        }
        AttendanceService.Row s = attendance.load(scope, req.sessionId());
        Token t = parse(req.token(), "P");
        if (!t.orgId().equals(s.orgId()) || !t.branchId().equals(s.branchId())) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if (clock.instant().isAfter(t.exp())) {
            throw new Exceptions("error.attendance.qrExpired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        attendance.assertOpen(s);
        int n = jdbc.update("insert into attendance_qr_use (jti, session_id, person_id, used_at) values (:j, :s, :p, :at) on conflict (jti) do nothing",
                new MapSqlParameterSource("j", t.jti()).addValue("s", s.id()).addValue("p", t.subject()).addValue("at", Timestamp.from(clock.instant())));
        if (n == 0) {
            throw new Exceptions("error.attendance.qrUsed", HttpStatus.CONFLICT);
        }
        return attendance.recordInternal(s, t.subject(), "PRESENT", "QR", actor.ownerId());
    }

    /** Autoservicio: la persona autenticada escanea el QR del culto [V9]. */
    @Transactional
    public AttendanceDtos.RecordResult self(AuthenticatedActor actor, AttendanceDtos.SelfRequest req) {
        Token t = parse(req == null ? null : req.token(), "S");
        if (!t.orgId().equals(actor.organizationId())) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        AttendanceService.Row s = attendance.loadRaw(t.subject());
        if (!s.orgId().equals(t.orgId()) || !s.branchId().equals(t.branchId())) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if (!s.selfCheckin()) {
            throw new Exceptions("error.attendance.selfNotAllowed", HttpStatus.FORBIDDEN);
        }
        Instant now = clock.instant();
        if (!"OPEN".equals(s.status()) || now.isBefore(s.startsAt().minus(SELF_BEFORE)) || now.isAfter(s.endsAt().plus(SELF_AFTER)) || now.isAfter(t.exp())) {
            throw new Exceptions("error.attendance.selfNotAllowed", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return attendance.recordInternal(s, actor.ownerId(), "PRESENT", "SELF", actor.ownerId());
    }

    // ---------------------------------------------------------------- firma

    private String sign(String type, UUID org, UUID branch, UUID subject, Instant exp, String jti) {
        String payload = String.join("|", PREFIX, type, org.toString(), branch.toString(), subject.toString(), String.valueOf(exp.getEpochSecond()), jti);
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return enc.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + enc.encodeToString(hmac(payload));
    }

    private Token parse(String raw, String expectedType) {
        try {
            if (raw == null || raw.isBlank() || raw.length() > 500) {
                throw new IllegalArgumentException();
            }
            String[] two = raw.trim().split("\\.");
            if (two.length != 2) {
                throw new IllegalArgumentException();
            }
            String payload = new String(Base64.getUrlDecoder().decode(two[0]), StandardCharsets.UTF_8);
            byte[] sig = Base64.getUrlDecoder().decode(two[1]);
            if (!MessageDigest.isEqual(sig, hmac(payload))) {
                throw new IllegalArgumentException();
            }
            String[] p = payload.split("\\|");
            if (p.length != 7 || !PREFIX.equals(p[0]) || !expectedType.equals(p[1])) {
                throw new IllegalArgumentException();
            }
            return new Token(p[1], UUID.fromString(p[2]), UUID.fromString(p[3]), UUID.fromString(p[4]), Instant.ofEpochSecond(Long.parseLong(p[5])), p[6]);
        } catch (RuntimeException e) {
            throw new Exceptions("error.attendance.qrInvalid", HttpStatus.BAD_REQUEST);
        }
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
