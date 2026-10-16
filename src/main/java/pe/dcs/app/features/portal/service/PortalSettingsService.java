package pe.dcs.app.features.portal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.dcs.app.features.config.service.OrgGuard;
import pe.dcs.app.features.portal.dto.PortalDtos.HomeBlock;
import pe.dcs.app.features.portal.dto.PortalDtos.SettingsRequest;
import pe.dcs.app.features.portal.dto.PortalDtos.SettingsResponse;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * M24 · ajustes del portal: una fila de organización (modo de alta, directorio, "ver hijos", texto legal — solo
 * ORG_ADMIN) y, opcionalmente, una fila por sede que solo puede tocar bloques de inicio y textos de bienvenida
 * (ORG_ADMIN o el ORG_BRANCH_ADMIN de esa sede).
 */
@Service
@RequiredArgsConstructor
public class PortalSettingsService {

    public static final String MODULE = "PORTAL";
    private static final TypeReference<List<HomeBlock>> BLOCKS = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final OrgGuard orgGuard;
    private final AuditService audit;
    private final Clock clock;
    private final PlatformTransactionManager txManager;

    private static final String SELECT = "select branch_id, signup_mode, directory_enabled, allow_children_view, legal_text_version,"
            + " cast(home_blocks as text) as home_blocks, welcome_text_es, welcome_text_en from portal_settings where organization_id = :o";

    @Transactional
    public SettingsResponse getOrg(UUID orgId) {
        ensureOrgRow(orgId);
        return selectOrg(orgId);
    }

    /**
     * Solo el SELECT de {@link #getOrg}, sin {@link #ensureOrgRow} (seguimiento 2026-09-26 — bug real hallado al
     * cablear el candado de {@code home_blocks}): {@link #updateOrg} ya llama {@code ensureOrgRow} al principio y
     * luego modifica esa misma fila con su propio {@code UPDATE}, todavía sin commitear (misma transacción). Si el
     * {@code return} final volviera a llamar a {@code getOrg} (que vuelve a llamar {@code ensureOrgRow}, con su
     * {@code REQUIRES_NEW} en una conexión NUEVA), ese INSERT ON CONFLICT DO NOTHING queda esperando el fin de la
     * transacción externa para resolver la fila — y la transacción externa es la del MISMO hilo, bloqueado
     * esperando esa llamada anidada: un auto-deadlock 100% reproducible (nunca detectado como deadlock por
     * Postgres porque no hay ciclo de locks, solo una espera de `transactionid` que jamás se resuelve). Por eso
     * {@code updateOrg} termina con este SELECT plano en vez de {@code getOrg}: para entonces ya se garantizó que
     * la fila existe (la llamada a {@code ensureOrgRow} del inicio del método, antes de tocar la fila).
     */
    private SettingsResponse selectOrg(UUID orgId) {
        return jdbc.query(SELECT + " and branch_id is null", new MapSqlParameterSource("o", orgId), (rs, i) -> map(rs, null)).get(0);
    }

    /** [D5] signup_mode/directory_enabled/allow_children_view/legal_text_version SIEMPRE vienen de la fila de organización
     * (spec: "override de sede solo en bloques+bienvenida"); solo home_blocks/welcome_text_* pueden venir de la sede. */
    @Transactional(readOnly = true)
    public SettingsResponse getBranch(UUID orgId, UUID branchId) {
        SettingsResponse org = getOrg(orgId);
        String name = branchName(branchId);
        List<SettingsResponse> l = jdbc.query(SELECT + " and branch_id = :b", new MapSqlParameterSource("o", orgId).addValue("b", branchId),
                (rs, i) -> map(rs, name));
        SettingsResponse branch = l.isEmpty() ? null : l.get(0);
        return new SettingsResponse(branchId, name, org.signupMode(), org.directoryEnabled(), org.allowChildrenView(), org.legalTextVersion(),
                branch == null ? org.homeBlocks() : branch.homeBlocks(), branch == null ? org.welcomeTextEs() : branch.welcomeTextEs(),
                branch == null ? org.welcomeTextEn() : branch.welcomeTextEn());
    }

    @Transactional
    public SettingsResponse updateOrg(AuthenticatedActor actor, AccessScope scope, SettingsRequest r) {
        orgGuard.requireOrgAdmin(actor);
        ensureOrgRow(scope.organizationId());
        String mode = r.signupMode() == null ? "INVITE_ONLY" : r.signupMode().trim().toUpperCase();
        if (!mode.equals("INVITE_ONLY") && !mode.equals("OPEN_WITH_APPROVAL")) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "signupMode");
        }
        jdbc.update("""
                update portal_settings set signup_mode = :m, directory_enabled = :d, allow_children_view = :c,
                    home_blocks = cast(:blocks as jsonb), welcome_text_es = :es, welcome_text_en = :en,
                    updated_at = :now, updated_by = :by, version = version + 1
                where organization_id = :o and branch_id is null
                """, params(scope, r, mode, actor.ownerId()));
        audit.record(new AuditService.Command(MODULE, "SETTINGS_UPDATE", "PortalSettings", scope.organizationId(), scope.organizationId(), null,
                java.util.Map.of("signupMode", mode)));
        return selectOrg(scope.organizationId());
    }

    @Transactional
    public SettingsResponse updateBranch(AuthenticatedActor actor, AccessScope scope, UUID branchId, SettingsRequest r) {
        if (!scope.canSeeBranch(branchId)) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        Timestamp now = Timestamp.from(clock.instant());
        int n = jdbc.update("""
                update portal_settings set home_blocks = cast(:blocks as jsonb), welcome_text_es = :es, welcome_text_en = :en,
                    updated_at = :now, updated_by = :by, version = version + 1
                where organization_id = :o and branch_id = :b
                """, new MapSqlParameterSource("o", scope.organizationId()).addValue("b", branchId).addValue("blocks", json(r.homeBlocks()))
                .addValue("es", r.welcomeTextEs()).addValue("en", r.welcomeTextEn()).addValue("now", now).addValue("by", actor.ownerId()));
        if (n == 0) {
            jdbc.update("""
                    insert into portal_settings (id, organization_id, branch_id, home_blocks, welcome_text_es, welcome_text_en, created_at, created_by)
                    values (:id, :o, :b, cast(:blocks as jsonb), :es, :en, :now, :by)
                    """, new MapSqlParameterSource("id", UUID.randomUUID()).addValue("o", scope.organizationId()).addValue("b", branchId)
                    .addValue("blocks", json(r.homeBlocks())).addValue("es", r.welcomeTextEs()).addValue("en", r.welcomeTextEn())
                    .addValue("now", now).addValue("by", actor.ownerId()));
        }
        audit.record(new AuditService.Command(MODULE, "SETTINGS_BRANCH_UPDATE", "PortalSettings", branchId, scope.organizationId(), branchId, java.util.Map.of()));
        return getBranch(scope.organizationId(), branchId);
    }

    /** Home efectivo que ve el propio miembro: bloques + bienvenida de su sede si existen, si no los de la organización. */
    @Transactional(readOnly = true)
    public SettingsResponse effectiveForMember(UUID orgId, UUID branchId) {
        return branchId == null ? getOrg(orgId) : getBranch(orgId, branchId);
    }

    /**
     * Alta perezosa de la fila de organización (seguimiento 2026-09-26 — bug real hallado al verificar FAMILY):
     * se llama también desde lecturas {@code readOnly = true} ({@link #effectiveForMember}, usado por
     * {@code home()}/{@code family()} del portal del miembro). Como es un método privado, un
     * {@code @Transactional} normal no lo intercepta (auto-invocación: no pasa por el proxy de Spring) y, aunque
     * lo interceptara, igual se uniría a la transacción física ya abierta como solo-lectura por el llamador. Se
     * usa {@link TransactionTemplate} con {@code REQUIRES_NEW} para forzar una transacción nueva, independiente
     * y de escritura solo para este INSERT — así el primer miembro que abre el portal de una organización nueva
     * no revienta con "cannot execute INSERT in a read-only transaction".
     */
    private void ensureOrgRow(UUID orgId) {
        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tt.executeWithoutResult(status -> jdbc.update(
                "insert into portal_settings (id, organization_id, branch_id, signup_mode, created_at) values (:id, :o, null, 'INVITE_ONLY', :now)"
                        + " on conflict do nothing",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("o", orgId).addValue("now", Timestamp.from(clock.instant()))));
    }

    private MapSqlParameterSource params(AccessScope scope, SettingsRequest r, String mode, UUID by) {
        return new MapSqlParameterSource("o", scope.organizationId()).addValue("m", mode)
                .addValue("d", Boolean.TRUE.equals(r.directoryEnabled())).addValue("c", Boolean.TRUE.equals(r.allowChildrenView()))
                .addValue("blocks", json(r.homeBlocks())).addValue("es", r.welcomeTextEs()).addValue("en", r.welcomeTextEn())
                .addValue("now", Timestamp.from(clock.instant())).addValue("by", by);
    }

    private SettingsResponse map(java.sql.ResultSet rs, String branchName) throws java.sql.SQLException {
        return new SettingsResponse((UUID) rs.getObject("branch_id"), branchName, rs.getString("signup_mode"), rs.getBoolean("directory_enabled"),
                rs.getBoolean("allow_children_view"), rs.getString("legal_text_version"), blocks(rs.getString("home_blocks")),
                rs.getString("welcome_text_es"), rs.getString("welcome_text_en"));
    }

    private String branchName(UUID branchId) {
        List<String> l = jdbc.queryForList("select name from branch where id = :id", new MapSqlParameterSource("id", branchId), String.class);
        return l.isEmpty() ? null : l.get(0);
    }

    private List<HomeBlock> blocks(String s) {
        try {
            return s == null ? List.of() : mapper.readValue(s, BLOCKS);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String json(List<HomeBlock> b) {
        try {
            return mapper.writeValueAsString(b == null ? List.of() : b);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
