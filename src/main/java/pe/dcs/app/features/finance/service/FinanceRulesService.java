package pe.dcs.app.features.finance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.finance.dto.FinanceDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * M15 · Reglas de finanzas de la organización (singleton, fila creada de antemano por el V26 en cada organización... en realidad se crea
 * perezosamente aquí la primera vez que se pide, con los valores por defecto de la migración: umbral de sede 500, umbral de adjunto 200,
 * overspendBlock/selfApproval en false). Solo ORG_ADMIN (N2) las administra vía {@code FIN_MOVEMENTS} [V3: umbrales >= 0].
 */
@Service
@RequiredArgsConstructor
public class FinanceRulesService {

    record Row(BigDecimal approvalThresholdBranch, BigDecimal attachmentThreshold, boolean overspendBlock, boolean selfApproval, java.time.Instant updatedAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final Clock clock;

    @Transactional
    public Row get(UUID orgId) {
        java.util.List<Row> r = jdbc.query("select approval_threshold_branch, attachment_threshold, overspend_block, self_approval, updated_at from fin_rules where organization_id = :o",
                new MapSqlParameterSource("o", orgId), (rs, i) -> new Row(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBoolean(3), rs.getBoolean(4),
                        rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant()));
        if (!r.isEmpty()) {
            return r.get(0);
        }
        jdbc.update("insert into fin_rules (organization_id) values (:o) on conflict (organization_id) do nothing", new MapSqlParameterSource("o", orgId));
        return get(orgId);
    }

    @Transactional(readOnly = true)
    public FinanceDtos.FinanceRulesView getView(AccessScope scope) {
        Row r = get(scope.organizationId());
        return new FinanceDtos.FinanceRulesView(r.approvalThresholdBranch().toPlainString(), r.attachmentThreshold().toPlainString(),
                r.overspendBlock(), r.selfApproval(), r.updatedAt());
    }

    @Transactional
    public FinanceDtos.FinanceRulesView update(AuthenticatedActor actor, AccessScope scope, FinanceDtos.FinanceRulesRequest r) {
        authz.require(actor, FinanceSupport.MOD_MOVEMENTS, Action.E);
        // El spec dice "solo ORG_ADMIN" para reglas/umbrales, pero FIN_MOVEMENTS.E también lo tiene un ORG_BRANCH_ADMIN
        // (BRANCH_ADMIN_CAPS no recorta este módulo, a propósito, según las instrucciones de la tarea). La lectura (GET/
        // getView) se deja abierta a ORG_BRANCH_ADMIN porque necesita conocer su propio umbral; la escritura sí se restringe aquí.
        if (scope.role() != pe.dcs.app.util.enums.RoleType.ORG_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        get(scope.organizationId());
        BigDecimal threshold = FinanceSupport.requiredAmount(r.approvalThresholdBranch(), "umbral de aprobación de sede");
        BigDecimal attachment = FinanceSupport.requiredAmount(r.attachmentThreshold(), "umbral de adjunto");
        if (threshold.signum() < 0 || attachment.signum() < 0) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "umbral");                                 // [V3]
        }
        jdbc.update("update fin_rules set approval_threshold_branch = :t, attachment_threshold = :a, overspend_block = :ob, self_approval = :sa,"
                        + " updated_at = :at, updated_by = :by where organization_id = :o",
                new MapSqlParameterSource("t", threshold).addValue("a", attachment).addValue("ob", Boolean.TRUE.equals(r.overspendBlock()))
                        .addValue("sa", Boolean.TRUE.equals(r.selfApproval())).addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId())
                        .addValue("o", scope.organizationId()));
        audit.record(new AuditService.Command(FinanceSupport.MOD_MOVEMENTS, "RULES_UPDATE", "FinanceRules", scope.organizationId(), scope.organizationId(), null,
                Map.of("approvalThresholdBranch", threshold.toPlainString(), "attachmentThreshold", attachment.toPlainString())));
        return getView(scope);
    }
}
