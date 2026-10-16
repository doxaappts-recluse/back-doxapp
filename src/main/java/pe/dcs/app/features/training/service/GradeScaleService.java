package pe.dcs.app.features.training.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.training.dto.TrainingDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/** M13 · Escala de notas por organización (mínimo, máximo, nota de aprobación) [V5]. Solo el administrador de la organización la edita. */
@Service
@RequiredArgsConstructor
public class GradeScaleService {

    static final BigDecimal DEFAULT_MIN = BigDecimal.ZERO;
    static final BigDecimal DEFAULT_MAX = BigDecimal.valueOf(20);
    static final BigDecimal DEFAULT_PASS = BigDecimal.valueOf(11);

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final Clock clock;

    record Scale(BigDecimal min, BigDecimal max, BigDecimal pass, Long version) {
    }

    @Transactional(readOnly = true)
    public Scale current(UUID orgId) {
        return jdbc.query("select grade_min, grade_max, grade_pass, version from training_grade_scale where organization_id = :o",
                        new MapSqlParameterSource("o", orgId), (rs, i) -> new Scale(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getLong(4)))
                .stream().findFirst().orElse(new Scale(DEFAULT_MIN, DEFAULT_MAX, DEFAULT_PASS, null));
    }

    @Transactional(readOnly = true)
    public TrainingDtos.GradeScaleResponse get(UUID orgId) {
        Scale s = current(orgId);
        return new TrainingDtos.GradeScaleResponse(s.min().toPlainString(), s.max().toPlainString(), s.pass().toPlainString(), s.version());
    }

    @Transactional
    public TrainingDtos.GradeScaleResponse update(AuthenticatedActor actor, AccessScope scope, TrainingDtos.GradeScaleRequest r) {
        authz.require(actor, TrainingSupport.MODULE, Action.E);
        if (scope.role() != RoleType.ORG_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        BigDecimal min = number(r.gradeMin(), "mínimo");
        BigDecimal max = number(r.gradeMax(), "máximo");
        BigDecimal pass = number(r.gradePass(), "nota de aprobación");
        if (min.compareTo(pass) >= 0 || pass.compareTo(max) > 0) {                                                    // [V5]
            throw new Exceptions("error.training.gradeScaleInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Scale cur = current(scope.organizationId());
        MapSqlParameterSource ps = new MapSqlParameterSource("o", scope.organizationId()).addValue("min", min).addValue("max", max).addValue("pass", pass)
                .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId());
        try {
            if (cur.version() == null) {
                jdbc.update("insert into training_grade_scale (organization_id, grade_min, grade_max, grade_pass, updated_at, updated_by) values (:o, :min, :max, :pass, :at, :by)", ps);
            } else {
                if (r.version() != null && !r.version().equals(cur.version())) {
                    throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
                }
                jdbc.update("update training_grade_scale set grade_min = :min, grade_max = :max, grade_pass = :pass, updated_at = :at, updated_by = :by,"
                        + " version = version + 1 where organization_id = :o", ps);
            }
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.training.gradeScaleInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        audit.record(new AuditService.Command(TrainingSupport.MODULE, "GRADE_SCALE", "GradeScale", scope.organizationId(), scope.organizationId(), null,
                Map.of("min", min.toPlainString(), "max", max.toPlainString(), "pass", pass.toPlainString())));
        return get(scope.organizationId());
    }

    private static BigDecimal number(String s, String label) {
        if (s == null || s.isBlank()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, label);
        }
        try {
            BigDecimal v = new BigDecimal(s.trim());
            if (v.compareTo(BigDecimal.ZERO) < 0 || v.compareTo(BigDecimal.valueOf(1000)) > 0) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, label);
            }
            return v;
        } catch (NumberFormatException e) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, label);
        }
    }
}
