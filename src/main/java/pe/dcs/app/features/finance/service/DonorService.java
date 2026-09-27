package pe.dcs.app.features.finance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.finance.dto.FinanceDtos;
import pe.dcs.app.features.person.service.PersonLookupService;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M15 · Donantes (FIN_DONORS): por persona de la organización (reutiliza {@link PersonLookupService}, único por persona) o
 * externo (nombre + documento, sin cuenta en el sistema). Sin sede: el donante es de toda la organización.
 */
@Service
@RequiredArgsConstructor
public class DonorService {

    private static final String ENTITY = "Donor";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final PersonLookupService persons;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<FinanceDtos.DonorView> search(AccessScope scope, FinanceDtos.DonorSearch req) {
        FinanceDtos.DonorSearch.DonorFilters f = req == null || req.filters() == null ? new FinanceDtos.DonorSearch.DonorFilters(null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId());
        StringBuilder w = new StringBuilder("d.organization_id = :org");
        if (FinanceSupport.hasText(f.q())) {
            ps.addValue("q", "%" + f.q().trim().toLowerCase().replace("%", "").replace("_", "") + "%");
            w.append(" and (lower(coalesce(d.external_name, '')) like :q or lower(coalesce(p.first_name || ' ' || p.last_name, '')) like :q"
                    + " or lower(coalesce(d.email, '')) like :q or coalesce(d.external_doc_id, '') like :q)");
        }
        String from = " from fin_donor d left join person p on p.id = d.person_id where ";
        Long total = jdbc.queryForObject("select count(*)" + from + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        // No se puede usar el alias "person_name" dentro de coalesce() en el ORDER BY: Postgres solo resuelve alias de
        // salida en referencias simples, no dentro de una expresión/función; se repite la expresión completa.
        List<FinanceDtos.DonorView> rows = jdbc.query("select d.*, trim(p.first_name || ' ' || p.last_name) as person_name" + from + w
                + " order by coalesce(trim(p.first_name || ' ' || p.last_name), d.external_name) limit :lim offset :off", ps, (rs, i) -> view(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public FinanceDtos.DonorView get(AccessScope scope, UUID id) {
        return jdbc.query("select d.*, trim(p.first_name || ' ' || p.last_name) as person_name from fin_donor d left join person p on p.id = d.person_id"
                        + " where d.id = :id and d.organization_id = :o", new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()),
                (rs, i) -> view(rs)).stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** El donante debe existir y ser de la organización (para enlazarlo en un movimiento). */
    @Transactional(readOnly = true)
    public void assertExists(UUID orgId, UUID donorId) {
        Integer n = jdbc.queryForObject("select count(*) from fin_donor where id = :id and organization_id = :o",
                new MapSqlParameterSource("id", donorId).addValue("o", orgId), Integer.class);
        if (n == null || n == 0) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
    }

    private void lock(AccessScope scope, UUID id) {
        get(scope, id);
        jdbc.queryForObject("select 1 from fin_donor where id = :id for update", new MapSqlParameterSource("id", id), Integer.class);
    }

    @Transactional
    public FinanceDtos.DonorView create(AuthenticatedActor actor, AccessScope scope, FinanceDtos.DonorRequest r) {
        authz.require(actor, FinanceSupport.MOD_DONORS, Action.C);
        String externalName = null;
        String externalDoc = null;
        if (r.personId() != null) {
            persons.getVisible(scope, r.personId());
        } else {
            externalName = FinanceSupport.trim(r.externalName(), 150, "nombre");
            if (externalName == null) {
                throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona o nombre");                 // ck_donor_identity
            }
            externalDoc = FinanceSupport.trim(r.externalDocId(), 20, "documento");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into fin_donor (id, organization_id, person_id, external_name, external_doc_id, email, created_at, created_by)"
                            + " values (:id, :o, :p, :en, :ed, :em, :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("p", r.personId()).addValue("en", externalName)
                            .addValue("ed", externalDoc).addValue("em", FinanceSupport.trim(r.email(), 150, "correo")).addValue("at", Timestamp.from(clock.instant()))
                            .addValue("by", scope.personId()));
        } catch (DuplicateKeyException e) {
            throw new Exceptions("error.common.duplicate", HttpStatus.CONFLICT, "persona");
        }
        audit.record(new AuditService.Command(FinanceSupport.MOD_DONORS, "CREATE", ENTITY, id, scope.organizationId(), null, Map.of()));
        return get(scope, id);
    }

    @Transactional
    public FinanceDtos.DonorView update(AuthenticatedActor actor, AccessScope scope, UUID id, FinanceDtos.DonorRequest r) {
        authz.require(actor, FinanceSupport.MOD_DONORS, Action.E);
        lock(scope, id);
        String email = FinanceSupport.trim(r.email(), 150, "correo");
        String externalName = FinanceSupport.trim(r.externalName(), 150, "nombre");
        String externalDoc = FinanceSupport.trim(r.externalDocId(), 20, "documento");
        int n = jdbc.update("update fin_donor set external_name = coalesce(:en, external_name), external_doc_id = coalesce(:ed, external_doc_id),"
                        + " email = :em, version = version + 1 where id = :id and (cast(:v as bigint) is null or version = :v)",
                new MapSqlParameterSource("en", externalName).addValue("ed", externalDoc).addValue("em", email).addValue("id", id).addValue("v", r.version()));
        if (n == 0) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(FinanceSupport.MOD_DONORS, "UPDATE", ENTITY, id, scope.organizationId(), null, Map.of()));
        return get(scope, id);
    }

    @Transactional
    public void delete(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, FinanceSupport.MOD_DONORS, Action.D);
        lock(scope, id);
        Integer used = jdbc.queryForObject("select count(*) from fin_movement where donor_id = :id", new MapSqlParameterSource("id", id), Integer.class);
        Integer pledged = jdbc.queryForObject("select count(*) from fin_pledge where donor_id = :id", new MapSqlParameterSource("id", id), Integer.class);
        if ((used != null && used > 0) || (pledged != null && pledged > 0)) {
            throw new Exceptions("error.common.hasDependencies", HttpStatus.CONFLICT);
        }
        jdbc.update("delete from fin_donor where id = :id", new MapSqlParameterSource("id", id));
        audit.record(new AuditService.Command(FinanceSupport.MOD_DONORS, "DELETE", ENTITY, id, scope.organizationId(), null, Map.of()));
    }

    private static FinanceDtos.DonorView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FinanceDtos.DonorView((UUID) rs.getObject("id"), (UUID) rs.getObject("person_id"), rs.getString("person_name"),
                rs.getString("external_name"), rs.getString("external_doc_id"), rs.getString("email"), rs.getTimestamp("created_at").toInstant(),
                rs.getLong("version"));
    }
}
