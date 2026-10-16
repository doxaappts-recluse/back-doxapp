package pe.dcs.app.features.training.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.doctemplate.dto.TemplateDtos;
import pe.dcs.app.features.doctemplate.service.DocumentTemplateService;
import pe.dcs.app.features.doctemplate.service.IssuedDocumentService;
import pe.dcs.app.features.doctemplate.service.TemplateSupport;
import pe.dcs.app.features.training.dto.TrainingDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.security.authz.ContractGate;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M13 · Certificado de formación, propio hasta que M18 unifique plantillas (mismo patrón que el de M08, pero por matrícula en vez de
 * por rito). Se emite solo (una vez) al aprobar; anularlo es una acción administrativa poco frecuente, con motivo.
 */
@Service
@RequiredArgsConstructor
public class TrainingCertificateService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ContractGate contractGate;
    private final DocumentTemplateService templates;
    private final IssuedDocumentService issuedDocs;

    @Transactional(readOnly = true)
    public TrainingDtos.CertificateResponse currentOrNull(UUID enrollmentId) {
        return jdbc.query("select c.*, trim(p.first_name || ' ' || p.last_name) as by_name from training_certificate c"
                        + " left join person p on p.id = c.issued_by where c.enrollment_id = :e order by (c.status = 'VALID') desc, c.issued_at desc limit 1",
                new MapSqlParameterSource("e", enrollmentId), (rs, i) -> map(rs)).stream().findFirst().orElse(null);
    }

    /** Emite el certificado si aún no hay uno VALID para esta matrícula [T08: reintento no duplica]. */
    @Transactional
    public TrainingDtos.CertificateResponse issue(AccessScope scope, UUID enrollmentId) {
        Integer valid = jdbc.queryForObject("select count(*) from training_certificate where enrollment_id = :e and status = 'VALID'",
                new MapSqlParameterSource("e", enrollmentId), Integer.class);
        if (valid != null && valid > 0) {
            return currentOrNull(enrollmentId);
        }
        Map<String, Object> row = jdbc.queryForMap("select e.branch_id, e.final_grade, trim(p.first_name || ' ' || p.last_name) as person_name, k.name as course_name,"
                        + " k.hours, b.name as branch_name, o.name as org_name from enrollment e join person p on p.id = e.person_id"
                        + " join course_class t on t.id = e.class_id join course k on k.id = t.course_id join branch b on b.id = e.branch_id"
                        + " join organization o on o.id = e.organization_id where e.id = :e",
                new MapSqlParameterSource("e", enrollmentId));
        UUID branch = (UUID) row.get("branch_id");
        Integer number = jdbc.queryForObject("insert into training_certificate_counter (organization_id, last_number) values (:o, 1)"
                        + " on conflict (organization_id) do update set last_number = training_certificate_counter.last_number + 1 returning last_number",
                new MapSqlParameterSource("o", scope.organizationId()), Integer.class);
        String no = "BIB-" + String.format("%06d", number);
        String code = code();
        UUID id = UUID.randomUUID();
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("personName", row.get("person_name"));
        snap.put("courseName", row.get("course_name"));
        snap.put("hours", row.get("hours"));
        snap.put("grade", row.get("final_grade") == null ? null : row.get("final_grade").toString());
        snap.put("branchName", row.get("branch_name"));
        snap.put("organizationName", row.get("org_name"));
        try {
            jdbc.update("insert into training_certificate (id, organization_id, branch_id, enrollment_id, number, certificate_no, code, status, issued_at, issued_by, snapshot)"
                            + " values (:id, :o, :b, :e, :n, :no, :c, 'VALID', :at, :by, cast(:s as jsonb))",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", branch).addValue("e", enrollmentId).addValue("n", number)
                            .addValue("no", no).addValue("c", code).addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId())
                            .addValue("s", mapper.writeValueAsString(snap)));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
        audit.record(new AuditService.Command(TrainingSupport.MODULE, "CERTIFICATE_ISSUE", "Certificate", id, scope.organizationId(), branch,
                Map.of("number", no, "enrollmentId", enrollmentId.toString())));
        return currentOrNull(enrollmentId);
    }

    @Transactional
    public TrainingDtos.CertificateResponse voidCertificate(pe.dcs.app.security.AuthenticatedActor actor, AccessScope scope, UUID enrollmentId, String reasonText) {
        authz.require(actor, TrainingSupport.MODULE, Action.S);
        if (scope.role() != RoleType.ORG_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        String reason = TrainingSupport.trim(reasonText, 300, "motivo");
        if (reason == null) {
            throw new Exceptions("error.common.reasonRequired", HttpStatus.BAD_REQUEST);
        }
        List<UUID> valid = jdbc.queryForList("select id from training_certificate where enrollment_id = :e and status = 'VALID'",
                new MapSqlParameterSource("e", enrollmentId), UUID.class);
        if (valid.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        jdbc.update("update training_certificate set status = 'VOIDED', void_reason = :r, voided_at = :at, voided_by = :by where id = :id",
                new MapSqlParameterSource("r", reason).addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", valid.get(0)));
        audit.record(new AuditService.Command(TrainingSupport.MODULE, "CERTIFICATE_VOID", "Certificate", valid.get(0), scope.organizationId(), null, Map.of("reason", reason)));
        return currentOrNull(enrollmentId);
    }

    /** Verificación pública: solo confirma que el certificado existe y su estado, con el nombre abreviado. */
    @Transactional(readOnly = true)
    public TrainingDtos.CertificateVerification verify(String codeRaw) {
        String code = codeRaw == null ? "" : codeRaw.trim().toUpperCase();
        if (code.length() < 6 || code.length() > 16) {
            return new TrainingDtos.CertificateVerification(false, null, null, null, null, null, null, null);
        }
        List<TrainingDtos.CertificateVerification> rows = jdbc.query("select c.status, c.certificate_no, c.issued_at, c.snapshot::text, o.name, b.name"
                + " from training_certificate c join organization o on o.id = c.organization_id join branch b on b.id = c.branch_id where c.code = :c",
                new MapSqlParameterSource("c", code), (rs, i) -> {
            Map<String, Object> snap = read(rs.getString(4));
            String holder = abbreviate(String.valueOf(snap.get("personName")));
            return new TrainingDtos.CertificateVerification(true, rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant(), holder,
                    String.valueOf(snap.get("courseName")), rs.getString(5), rs.getString(6));
        });
        return rows.isEmpty() ? new TrainingDtos.CertificateVerification(false, null, null, null, null, null, null, null) : rows.get(0);
    }

    private TrainingDtos.CertificateResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp v = rs.getTimestamp("voided_at");
        return new TrainingDtos.CertificateResponse((UUID) rs.getObject("id"), (UUID) rs.getObject("enrollment_id"), rs.getString("certificate_no"), rs.getString("code"),
                rs.getString("status"), rs.getTimestamp("issued_at").toInstant(), rs.getString("by_name"), rs.getString("void_reason"),
                v == null ? null : v.toInstant(), read(rs.getString("snapshot")), (UUID) rs.getObject("issued_document_id"));
    }

    // ---------------------------------------------------------------- enlace con M18 (seguimiento 2026-09-25)

    /**
     * Mismo criterio que {@link pe.dcs.app.features.rite.service.CertificateService#tryLinkM18}: si la organización
     * tiene DOC_TEMPLATES contratado y ya publicó una plantilla predeterminada para BIBLE_ACADEMY_CERTIFICATE (org o
     * sede), emite además el documento real con el motor de M18 y lo enlaza; si no, no hace nada — el certificado
     * propio (ya emitido por {@link #issue}) sigue siendo válido y suficiente. Se llama en su propia transacción,
     * DESPUÉS de que {@code EnrollmentService.status()} ya confirmó su propia transacción (nunca desde dentro de
     * ella): el llamador (controlador) es quien la invoca, para no anidarla en la transacción de la matrícula.
     */
    @Transactional
    public void tryLinkM18(AuthenticatedActor actor, AccessScope scope, UUID enrollmentId) {
        TrainingDtos.CertificateResponse cert = currentOrNull(enrollmentId);
        if (cert == null || cert.issuedDocumentId() != null || !"VALID".equals(cert.status())) {
            return;
        }
        if (!contractGate.enabled(scope.organizationId(), TemplateSupport.MODULE)) {
            return;
        }
        Map<String, Object> row = jdbc.queryForMap("select e.branch_id, k.name as course_name, k.hours from enrollment e"
                        + " join course_class t on t.id = e.class_id join course k on k.id = t.course_id where e.id = :e",
                new MapSqlParameterSource("e", enrollmentId));
        UUID branchId = (UUID) row.get("branch_id");
        if (templates.defaultTemplateId(scope.organizationId(), "BIBLE_ACADEMY_CERTIFICATE", branchId) == null) {
            return;
        }
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("person.fullName", String.valueOf(cert.snapshot().get("personName")));
        vars.put("course.name", String.valueOf(row.get("course_name")));
        vars.put("course.hours", row.get("hours") == null ? "" : String.valueOf(row.get("hours")));
        TemplateDtos.IssuedView issued = issuedDocs.issue(actor, scope, new TemplateDtos.IssueRequest(branchId, "BIBLE_ACADEMY_CERTIFICATE", "TRAINING_CERTIFICATE", cert.id(), vars, null));
        jdbc.update("update training_certificate set issued_document_id = :d where id = :id", new MapSqlParameterSource("d", issued.id()).addValue("id", cert.id()));
    }

    /** Contraparte de {@link #tryLinkM18}: si el certificado recién anulado tenía un documento de M18 enlazado, lo anula también. */
    @Transactional
    public void tryUnlinkM18(AuthenticatedActor actor, AccessScope scope, UUID certificateId) {
        List<UUID> docIdRows = jdbc.query("select issued_document_id from training_certificate where id = :id", new MapSqlParameterSource("id", certificateId), (rs, i) -> (UUID) rs.getObject(1));
        UUID docId = docIdRows.isEmpty() ? null : docIdRows.get(0);
        if (docId != null) {
            issuedDocs.voidDocument(actor, scope, docId, "Certificado de origen anulado");
        }
    }

    private Map<String, Object> read(String json) {
        try {
            return mapper.readValue(json, MAP);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private static String code() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    static String abbreviate(String full) {
        if (full == null || full.isBlank() || "null".equals(full)) {
            return "";
        }
        String[] parts = full.trim().split("\\s+");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(' ').append(parts[i].charAt(0)).append('.');
        }
        return sb.toString();
    }
}
