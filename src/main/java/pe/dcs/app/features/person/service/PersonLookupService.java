package pe.dcs.app.features.person.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.vo.DocumentType;
import pe.dcs.app.shared.vo.DocumentValidator;
import pe.dcs.app.util.Exceptions;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Núcleo 01 · PersonLookupService. ÚNICO camino para que otros módulos busquen o resuelvan personas (nadie consulta la tabla
 * {@code person} por su cuenta ni crea personas por otro lado). Siempre acotado a la organización; las búsquedas para elegir
 * (PersonPicker) además respetan el alcance de sedes y solo devuelven personas activas.
 */
@Service
@RequiredArgsConstructor
public class PersonLookupService {

    /** Datos mínimos de una persona: suficientes para mostrarla en un selector o en una fila. */
    public record PersonMin(UUID id, String fullName, DocumentType docType, String docNumber, String status, LocalDate birthDate,
                            UUID branchId, String branchName) {
    }

    private static final String SELECT = "select p.id, p.first_name, p.last_name, p.doc_type, p.doc_number, p.status, p.birth_date, "
            + "p.primary_branch_id, b.name as branch_name from person p left join branch b on b.id = p.primary_branch_id ";

    private final NamedParameterJdbcTemplate jdbc;

    /** Persona de la organización con ese documento (sin filtrar por sedes: el llamador decide qué mostrar). */
    @Transactional(readOnly = true)
    public Optional<PersonMin> find(UUID orgId, DocumentType type, String docNumber) {
        MapSqlParameterSource ps = new MapSqlParameterSource("org", orgId).addValue("t", type.name()).addValue("d", DocumentValidator.normalize(docNumber));
        return jdbc.query(SELECT + "where p.organization_id = :org and p.doc_type = :t and p.doc_number = :d", ps, (rs, i) -> map(rs)).stream().findFirst();
    }

    /** Persona por id dentro de la organización; 404 si no existe o es de otra organización. */
    @Transactional(readOnly = true)
    public PersonMin get(UUID orgId, UUID personId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("org", orgId).addValue("id", personId);
        return jdbc.query(SELECT + "where p.organization_id = :org and p.id = :id", ps, (rs, i) -> map(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** Persona anonimizada: ya no admite cambios (foto, autorizaciones, edición). */
    @Transactional(readOnly = true)
    public boolean isAnonymized(UUID personId) {
        Boolean b = jdbc.queryForObject("select anonymized_at is not null from person where id = :id", new MapSqlParameterSource("id", personId), Boolean.class);
        return Boolean.TRUE.equals(b);
    }

    /** Ídem, pero 404 también si la persona está fuera del alcance de sedes de quien consulta [V16]. */
    @Transactional(readOnly = true)
    public PersonMin getVisible(AccessScope scope, UUID personId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId()).addValue("id", personId);
        String vis = PersonScope.visible(scope, ps, "p");
        return jdbc.query(SELECT + "where p.organization_id = :org and p.id = :id and " + vis, ps, (rs, i) -> map(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** Búsqueda para el selector: personas ACTIVAS del alcance por nombre, documento, correo o teléfono. */
    @Transactional(readOnly = true)
    public List<PersonMin> search(AccessScope scope, String q, int limit) {
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId()).addValue("lim", Math.max(1, Math.min(limit, 30)));
        String vis = PersonScope.visible(scope, ps, "p");
        StringBuilder where = new StringBuilder("where p.organization_id = :org and p.status = 'ACTIVE' and ").append(vis);
        String text = q == null ? "" : q.trim().toLowerCase();
        if (!text.isEmpty()) {
            ps.addValue("like", "%" + text.replace("%", "").replace("_", "") + "%");
            where.append(" and (lower(p.first_name || ' ' || p.last_name) like :like or lower(p.last_name || ' ' || p.first_name) like :like"
                    + " or lower(p.doc_number) like :like or lower(coalesce(p.email, '')) like :like or coalesce(p.phone, '') like :like)");
        }
        return jdbc.query(SELECT + where + " order by lower(p.last_name), lower(p.first_name) limit :lim", ps, (rs, i) -> map(rs));
    }

    /** Coincidencias visibles (por documento, teléfono o correo exactos, sin importar el estado) y si el documento está en otra sede no visible. */
    public record Matches(List<PersonMin> people, boolean documentInOtherBranch) {
    }

    /** Busca personas ya registradas con esos datos para proponer vincular en lugar de duplicar (visitantes, V9). */
    @Transactional(readOnly = true)
    public Matches findMatches(AccessScope scope, DocumentType type, String docNumber, String phone, String email) {
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId());
        List<String> ors = new java.util.ArrayList<>();
        String doc = docNumber == null ? null : DocumentValidator.normalize(docNumber);
        if (type != null && doc != null && !doc.isBlank()) {
            ps.addValue("t", type.name()).addValue("d", doc);
            ors.add("(p.doc_type = :t and p.doc_number = :d)");
        }
        if (phone != null && !phone.isBlank()) {
            ps.addValue("ph", phone);
            ors.add("p.phone = :ph or p.whatsapp = :ph");
        }
        if (email != null && !email.isBlank()) {
            ps.addValue("em", email.trim().toLowerCase());
            ors.add("lower(p.email) = :em");
        }
        if (ors.isEmpty()) {
            return new Matches(List.of(), false);
        }
        MapSqlParameterSource vps = new MapSqlParameterSource();
        String vis = PersonScope.visible(scope, vps, "p");
        ps.addValues(vps.getValues());
        List<PersonMin> visible = jdbc.query(SELECT + "where p.organization_id = :org and p.status <> 'MERGED' and (" + String.join(" or ", ors)
                + ") and " + vis + " order by lower(p.last_name), lower(p.first_name) limit 10", ps, (rs, i) -> map(rs));
        boolean other = false;
        if (type != null && doc != null && !doc.isBlank()) {
            PersonMin byDoc = find(scope.organizationId(), type, doc).orElse(null);
            other = byDoc != null && visible.stream().noneMatch(v -> v.id().equals(byDoc.id()));
        }
        return new Matches(visible, other);
    }

    private static PersonMin map(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Date bd = rs.getDate("birth_date");
        return new PersonMin((UUID) rs.getObject("id"), (rs.getString("first_name") + " " + rs.getString("last_name")).trim(),
                rs.getString("doc_type") == null ? null : DocumentType.valueOf(rs.getString("doc_type")), rs.getString("doc_number"), rs.getString("status"),
                bd == null ? null : bd.toLocalDate(), (UUID) rs.getObject("primary_branch_id"), rs.getString("branch_name"));
    }
}
