package pe.dcs.app.features.rite.service;

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
import pe.dcs.app.features.rite.dto.RiteDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.security.authz.ContractGate;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M08 · Certificados de los ritos realizados. El correlativo es por organización y tipo, sin saltos [V12]; solo hay un certificado vigente por rito;
 * anular pide motivo (acción A) y no borra nada. La plantilla y el PDF llegan con M18: aquí se guarda el registro, el código de verificación y una copia de los datos.
 */
@Service
@RequiredArgsConstructor
public class CertificateService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final NamedParameterJdbcTemplate jdbc;
    private final RiteService rites;
    private final AuthorizationService authz;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final Clock clock;
    private final ContractGate contractGate;
    private final DocumentTemplateService templates;
    private final IssuedDocumentService issuedDocs;

    @Transactional(readOnly = true)
    public RiteDtos.CertificateResponse current(AccessScope scope, String type, UUID riteId) {
        String t = RiteService.type(type);
        rites.get(scope, t, riteId);                                                                                    // visibilidad
        return jdbc.query("select c.*, trim(p.first_name || ' ' || p.last_name) as by_name from certificate c left join person p on p.id = c.issued_by where c.rite_id = :r"
                        + " order by (c.status = 'VALID') desc, c.issued_at desc limit 1", new MapSqlParameterSource("r", riteId), (rs, i) -> map(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.rite.certificateNotFound", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public RiteDtos.CertificateResponse issue(AuthenticatedActor actor, AccessScope scope, String type, UUID riteId) {
        String t = RiteService.type(type);
        authz.require(actor, RiteSupport.moduleOf(t), Action.E);
        List<Object[]> r = lockRite(scope, t, riteId);
        if (!"COMPLETED".equals(r.get(0)[0])) {
            throw new Exceptions("error.rite.notCompleted", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        UUID branch = (UUID) r.get(0)[1];
        Integer valid = jdbc.queryForObject("select count(*) from certificate where rite_id = :r and status = 'VALID'", new MapSqlParameterSource("r", riteId), Integer.class);
        if (valid != null && valid > 0) {
            throw new Exceptions("error.rite.certificateExists", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Integer number = jdbc.queryForObject("insert into certificate_counter (organization_id, rite_type, last_number) values (:o, :t, 1)"
                        + " on conflict (organization_id, rite_type) do update set last_number = certificate_counter.last_number + 1 returning last_number",
                new MapSqlParameterSource("o", scope.organizationId()).addValue("t", t), Integer.class);
        String no = prefix(t) + "-" + String.format("%06d", number);
        String code = code();
        UUID id = UUID.randomUUID();
        RiteDtos.RiteResponse rite = rites.get(scope, t, riteId);
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("riteType", t);
        snap.put("personName", rite.personName());
        if (rite.person2Name() != null) {
            snap.put("person2Name", rite.person2Name());
        }
        snap.put("eventDate", rite.date() == null ? null : rite.date().toString());
        snap.put("place", rite.place());
        snap.put("officiant", rite.officiantName() != null ? rite.officiantName() : rite.officiantText());
        snap.put("external", rite.external());
        snap.put("externalChurch", rite.externalChurch());
        snap.put("civilRecordNo", rite.civilRecordNo());
        snap.put("guardians", rite.guardians().stream().map(RiteDtos.PersonRef::name).toList());
        snap.put("branchName", rite.branchName());
        snap.put("organizationName", jdbc.queryForObject("select name from organization where id = :o", new MapSqlParameterSource("o", scope.organizationId()), String.class));
        try {
            jdbc.update("insert into certificate (id, organization_id, branch_id, rite_type, rite_id, number, certificate_no, code, status, issued_at, issued_by, snapshot)"
                            + " values (:id, :o, :b, :t, :r, :n, :no, :c, 'VALID', :at, :by, cast(:s as jsonb))",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", branch).addValue("t", t).addValue("r", riteId).addValue("n", number)
                            .addValue("no", no).addValue("c", code).addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("s", mapper.writeValueAsString(snap)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        jdbc.update("update rite set certificate_no = :no where id = :r", new MapSqlParameterSource("no", no).addValue("r", riteId));
        audit.record(new AuditService.Command(RiteSupport.moduleOf(t), "CERTIFICATE_ISSUE", "Certificate", id, scope.organizationId(), branch, Map.of("number", no, "riteId", riteId.toString())));
        return current(scope, t, riteId);
    }

    @Transactional
    public RiteDtos.CertificateResponse voidCertificate(AuthenticatedActor actor, AccessScope scope, String type, UUID riteId, String reasonText) {
        String t = RiteService.type(type);
        authz.require(actor, RiteSupport.moduleOf(t), Action.A);
        List<Object[]> r = lockRite(scope, t, riteId);
        String reason = RiteSupport.trim(reasonText, 300);
        if (reason == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "motivo");
        }
        List<UUID> valid = jdbc.queryForList("select id from certificate where rite_id = :r and status = 'VALID'", new MapSqlParameterSource("r", riteId), UUID.class);
        if (valid.isEmpty()) {
            throw new Exceptions("error.rite.certificateNotFound", HttpStatus.NOT_FOUND);
        }
        jdbc.update("update certificate set status = 'VOIDED', void_reason = :v, voided_at = :at, voided_by = :by where id = :id",
                new MapSqlParameterSource("v", reason).addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", valid.get(0)));
        jdbc.update("update rite set certificate_no = null where id = :r", new MapSqlParameterSource("r", riteId));
        audit.record(new AuditService.Command(RiteSupport.moduleOf(t), "CERTIFICATE_VOID", "Certificate", valid.get(0), scope.organizationId(), (UUID) r.get(0)[1], Map.of("reason", reason)));
        return current(scope, t, riteId);
    }

    /** Verificación pública: solo confirma que el certificado existe y su estado, con el nombre abreviado. */
    @Transactional(readOnly = true)
    public RiteDtos.CertificateVerification verify(String codeRaw) {
        String code = codeRaw == null ? "" : codeRaw.trim().toUpperCase();
        if (code.length() < 6 || code.length() > 16) {
            return new RiteDtos.CertificateVerification(false, null, null, null, null, null, null, null, null);
        }
        List<RiteDtos.CertificateVerification> rows = jdbc.query("select c.status, c.rite_type, c.certificate_no, c.issued_at, c.snapshot::text, o.name, b.name from certificate c"
                + " join organization o on o.id = c.organization_id join branch b on b.id = c.branch_id where c.code = :c", new MapSqlParameterSource("c", code), (rs, i) -> {
            Map<String, Object> snap = read(rs.getString(5));
            String holder = abbreviate(String.valueOf(snap.get("personName")));
            if (snap.get("person2Name") != null) {
                holder += " & " + abbreviate(String.valueOf(snap.get("person2Name")));
            }
            Object d = snap.get("eventDate");
            return new RiteDtos.CertificateVerification(true, rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant(), holder, rs.getString(6), rs.getString(7),
                    d == null ? null : LocalDate.parse(String.valueOf(d)));
        });
        return rows.isEmpty() ? new RiteDtos.CertificateVerification(false, null, null, null, null, null, null, null, null) : rows.get(0);
    }

    // ---------------------------------------------------------------- utilidades

    private List<Object[]> lockRite(AccessScope scope, String t, UUID riteId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", riteId).addValue("t", t);
        String w = RiteSupport.writable(scope, ps, "r");
        List<Object[]> r = jdbc.query("select r.status, r.branch_id from rite r where r.id = :id and r.rite_type = :t and " + w + " for update", ps,
                (rs, i) -> new Object[]{rs.getString(1), rs.getObject(2)});
        if (r.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return r;
    }

    private RiteDtos.CertificateResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp v = rs.getTimestamp("voided_at");
        return new RiteDtos.CertificateResponse((UUID) rs.getObject("id"), rs.getString("rite_type"), (UUID) rs.getObject("rite_id"), rs.getString("certificate_no"), rs.getString("code"),
                rs.getString("status"), rs.getTimestamp("issued_at").toInstant(), rs.getString("by_name"), rs.getString("void_reason"), v == null ? null : v.toInstant(),
                read(rs.getString("snapshot")), (UUID) rs.getObject("issued_document_id"));
    }

    // ---------------------------------------------------------------- enlace con M18 (seguimiento 2026-09-25)

    private static String m18Type(String riteType) {
        return switch (riteType) {
            case "BAPTISM" -> "BAPTISM_CERTIFICATE";
            case "MARRIAGE" -> "MARRIAGE_CERTIFICATE";
            default -> "CHILD_DEDICATION_CERTIFICATE";                                                                    // DEDICATION
        };
    }

    /**
     * Si la organización tiene DOC_TEMPLATES contratado y ya publicó una plantilla predeterminada para este tipo
     * (org o sede), emite además el documento real (PDF+QR) con el motor de M18 y lo enlaza en
     * {@code certificate.issued_document_id}; si no, no hace nada — el certificado propio (ya emitido por
     * {@link #issue}) sigue siendo válido y suficiente exactamente como antes. Se llama **después** de que
     * {@link #issue} ya confirmó su propia transacción (nunca desde dentro de ella): esta es su propia transacción
     * nueva, así que si algo falla acá (plantilla corrupta, error de render) solo se deshace esto, nunca el
     * certificado ya emitido — el llamador (controlador) atrapa cualquier excepción sin dejar que rompa la
     * respuesta de {@code issue()}.
     */
    @Transactional
    public void tryLinkM18(AuthenticatedActor actor, AccessScope scope, String type, UUID riteId) {
        String t = RiteService.type(type);
        RiteDtos.CertificateResponse cert = current(scope, t, riteId);
        if (cert.issuedDocumentId() != null || !"VALID".equals(cert.status())) {
            return;
        }
        if (!contractGate.enabled(scope.organizationId(), TemplateSupport.MODULE)) {
            return;
        }
        RiteDtos.RiteResponse rite = rites.get(scope, t, riteId);
        String m18Type = m18Type(t);
        if (templates.defaultTemplateId(scope.organizationId(), m18Type, rite.branchId()) == null) {
            return;
        }
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("person.fullName", rite.personName());
        if (rite.person2Name() != null) {
            vars.put("person2.fullName", rite.person2Name());
        }
        vars.put("rite.date", rite.date() == null ? "" : rite.date().toString());
        vars.put("officiant", rite.officiantName() != null ? rite.officiantName() : (rite.officiantText() != null ? rite.officiantText() : ""));
        TemplateDtos.IssuedView issued = issuedDocs.issue(actor, scope, new TemplateDtos.IssueRequest(rite.branchId(), m18Type, "RITE_CERTIFICATE", cert.id(), vars, null));
        jdbc.update("update certificate set issued_document_id = :d where id = :id", new MapSqlParameterSource("d", issued.id()).addValue("id", cert.id()));
    }

    /** Contraparte de {@link #tryLinkM18}: si el certificado que se acaba de anular tenía un documento de M18 enlazado, lo anula también (misma transacción propia, mismo criterio de aislamiento). */
    @Transactional
    public void tryUnlinkM18(AuthenticatedActor actor, AccessScope scope, UUID certificateId) {
        List<UUID> docIdRows = jdbc.query("select issued_document_id from certificate where id = :id", new MapSqlParameterSource("id", certificateId), (rs, i) -> (UUID) rs.getObject(1));
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

    static String prefix(String t) {
        return switch (t) {
            case "BAPTISM" -> "BAU";
            case "MARRIAGE" -> "MAT";
            default -> "PRE";
        };
    }

    private static String code() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** «María Gómez Pérez» → «María G. P.». */
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
