package pe.dcs.app.features.doctemplate.service;

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
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.storage.FileStorageService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M18 · Motor general de emisión de documentos ({@code issued_document}): numera [V13], genera el código de
 * verificación, renderiza el PDF con {@link DocumentRenderService} y lo guarda vía {@link FileStorageService}.
 * [D1] los certificados de M08/M13 siguen con su propio registro y snapshot; este motor es para los tipos que
 * ningún módulo anterior cubre (ver encabezado de V29__templates_certificates_m18.sql).
 */
@Service
@RequiredArgsConstructor
public class IssuedDocumentService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    private static final String SELECT = "select d.*, b.name as branch_name, trim(coalesce(p.first_name || ' ' || p.last_name, '')) as issued_by_name"
            + " from issued_document d join branch b on b.id = d.branch_id left join person p on p.id = d.issued_by ";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final DocumentTemplateService templates;
    private final SignatoryService signatories;
    private final DocumentRenderService renderer;
    private final FileStorageService storage;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<TemplateDtos.IssuedView> search(AccessScope scope, TemplateDtos.IssuedSearch req) {
        TemplateDtos.IssuedSearch.IssuedFilters f = req == null || req.filters() == null ? new TemplateDtos.IssuedSearch.IssuedFilters(null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(TemplateSupport.visibleByBranch(scope, ps, "d", false));
        if (f.branchId() != null) {
            w.append(" and d.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (TemplateSupport.hasText(f.type())) {
            w.append(" and d.type = :ft");
            ps.addValue("ft", TemplateSupport.type(f.type()));
        }
        if (TemplateSupport.hasText(f.status())) {
            w.append(" and d.status = :fs");
            ps.addValue("fs", f.status().trim().toUpperCase());
        }
        if (f.subjectId() != null) {
            w.append(" and d.subject_id = :fsub");
            ps.addValue("fsub", f.subjectId());
        }
        Long total = jdbc.queryForObject("select count(*) from issued_document d where " + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<TemplateDtos.IssuedView> rows = jdbc.query(SELECT + "where " + w + " order by d.issued_at desc limit :lim offset :off", ps, (rs, i) -> map(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public TemplateDtos.IssuedView get(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String w = TemplateSupport.visibleByBranch(scope, ps, "d", false);
        return jdbc.query(SELECT + "where d.id = :id and " + w, ps, (rs, i) -> map(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** [V10][V11] emite un documento nuevo con la plantilla por defecto de (tipo, sede); un tipo+sujeto vigente a la vez salvo reimpresión. */
    @Transactional
    public TemplateDtos.IssuedView issue(AuthenticatedActor actor, AccessScope scope, TemplateDtos.IssueRequest req) {
        String type = TemplateSupport.type(req.type());
        UUID branchId = req.branchId();
        if (branchId == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
        }
        if (req.subjectType() == null || req.subjectType().isBlank() || req.subjectId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sujeto");
        }
        if (req.signatoryId() != null) {
            Integer sig = jdbc.queryForObject("select count(*) from signatory where id = :id and organization_id = :o and active",
                    new MapSqlParameterSource("id", req.signatoryId()).addValue("o", scope.organizationId()), Integer.class);
            if (sig == null || sig == 0) {
                throw new Exceptions("error.document.signatoryInvalid", HttpStatus.BAD_REQUEST);
            }
        }
        Integer existing = jdbc.queryForObject("select count(*) from issued_document where organization_id = :o and type = :t and subject_id = :s and status = 'ISSUED'",
                new MapSqlParameterSource("o", scope.organizationId()).addValue("t", type).addValue("s", req.subjectId()), Integer.class);
        if (existing != null && existing > 0) {
            throw new Exceptions("error.document.alreadyIssued", HttpStatus.UNPROCESSABLE_ENTITY);                                       // [V11]
        }
        UUID templateId = templates.defaultTemplateId(scope.organizationId(), type, branchId);
        if (templateId == null) {
            throw new Exceptions("error.document.noTemplate", HttpStatus.UNPROCESSABLE_ENTITY);                                          // [V10]
        }
        Map<String, Object> tv = templates.designAndVersion(templateId);
        @SuppressWarnings("unchecked")
        Map<String, Object> design = (Map<String, Object>) tv.get("design");
        int templateVersion = (int) tv.get("version");

        int year = LocalDate.now(clock).getYear();
        Integer number = jdbc.queryForObject("insert into issued_document_counter (organization_id, type, year, last_number) values (:o, :t, :y, 1)"
                        + " on conflict (organization_id, type, year) do update set last_number = issued_document_counter.last_number + 1 returning last_number",
                new MapSqlParameterSource("o", scope.organizationId()).addValue("t", type).addValue("y", year), Integer.class);
        String documentNo = prefix(type) + "-" + year + "-" + String.format("%06d", number);
        String code = code();
        UUID id = UUID.randomUUID();

        Map<String, String> variables = new LinkedHashMap<>(req.variables() == null ? Map.of() : req.variables());
        String orgName = jdbc.queryForObject("select name from organization where id = :o", new MapSqlParameterSource("o", scope.organizationId()), String.class);
        String branchName = jdbc.queryForObject("select name from branch where id = :b", new MapSqlParameterSource("b", branchId), String.class);
        variables.put("org.displayName", orgName);
        variables.put("org.name", orgName);
        variables.put("branch.name", branchName);
        variables.put("number", documentNo);
        variables.put("date", LocalDate.now(clock).toString());

        String verificationUrl = "https://doxapp.example/v/" + code;
        byte[] qr = renderer.qrPng(verificationUrl, 220);
        resolveImageBytes(design, req.signatoryId());
        DocumentRenderService.RenderResult r = renderer.render(design, variables, qr, code);
        String pdfKey = "org/" + scope.organizationId() + "/issued-documents/" + id + ".pdf";
        storage.put(pdfKey, r.pdf(), "application/pdf");

        Map<String, Object> snapshot = new LinkedHashMap<>(variables);
        jdbc.update("insert into issued_document (id, organization_id, branch_id, type, template_id, template_version, subject_type, subject_id, number,"
                        + " document_no, verification_code, signatory_id, status, pdf_ref, sha256, is_duplicate, snapshot, issued_at, issued_by)"
                        + " values (:id, :o, :b, :t, :tpl, :tv, :st, :sid, :n, :no, :c, :sig, 'ISSUED', :pdf, :sha, false, cast(:snap as jsonb), :at, :by)",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", branchId).addValue("t", type).addValue("tpl", templateId)
                        .addValue("tv", templateVersion).addValue("st", req.subjectType().trim().toUpperCase()).addValue("sid", req.subjectId()).addValue("n", number)
                        .addValue("no", documentNo).addValue("c", code).addValue("sig", req.signatoryId()).addValue("pdf", pdfKey).addValue("sha", r.sha256())
                        .addValue("snap", writeJson(snapshot)).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.contextId()));
        audit.record(new AuditService.Command(TemplateSupport.MODULE, "DOCUMENT_ISSUE", "IssuedDocument", id, scope.organizationId(), branchId, Map.of("documentNo", documentNo)));
        return get(scope, id);
    }

    /** Reimpresión: mismo número y código, vuelve a renderizar con la misma plantilla/variables guardadas; marca {@code is_duplicate}. */
    @Transactional
    public TemplateDtos.IssuedView reissue(AuthenticatedActor actor, AccessScope scope, UUID id) {
        TemplateDtos.IssuedView cur = get(scope, id);
        if (!"ISSUED".equals(cur.status())) {
            throw new Exceptions("error.document.voided", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Map<String, Object> tv = templates.designAndVersion(cur.templateId());
        @SuppressWarnings("unchecked")
        Map<String, Object> design = (Map<String, Object>) tv.get("design");
        Map<String, String> snapshot = jdbc.query("select snapshot::text from issued_document where id = :id", new MapSqlParameterSource("id", id),
                (rs, i) -> readSnapshot(rs.getString(1))).stream().findFirst().orElseThrow();
        String verificationUrl = "https://doxapp.example/v/" + cur.verificationCode();
        byte[] qr = renderer.qrPng(verificationUrl, 220);
        UUID signatoryId = jdbc.queryForObject("select signatory_id from issued_document where id = :id", new MapSqlParameterSource("id", id), UUID.class);
        resolveImageBytes(design, signatoryId);
        DocumentRenderService.RenderResult r = renderer.render(design, snapshot, qr, cur.verificationCode());
        String pdfKey = "org/" + scope.organizationId() + "/issued-documents/" + id + "-r" + System.currentTimeMillis() + ".pdf";
        storage.put(pdfKey, r.pdf(), "application/pdf");
        jdbc.update("update issued_document set pdf_ref = :pdf, sha256 = :sha, is_duplicate = true where id = :id",
                new MapSqlParameterSource("pdf", pdfKey).addValue("sha", r.sha256()).addValue("id", id));
        audit.record(new AuditService.Command(TemplateSupport.MODULE, "DOCUMENT_REISSUE", "IssuedDocument", id, scope.organizationId(), cur.branchId(), Map.of()));
        return get(scope, id);
    }

    /** [V12] anular exige motivo; no borra el registro, solo cambia su estado (libera el (tipo, sujeto) para uno nuevo). */
    @Transactional
    public TemplateDtos.IssuedView voidDocument(AuthenticatedActor actor, AccessScope scope, UUID id, String reasonText) {
        TemplateDtos.IssuedView cur = get(scope, id);
        String reason = TemplateSupport.trim(reasonText, 300, "motivo");
        if (reason == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "motivo");
        }
        if (!"ISSUED".equals(cur.status())) {
            throw new Exceptions("error.document.voided", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        jdbc.update("update issued_document set status = 'VOIDED', void_reason = :r, voided_at = :at, voided_by = :by where id = :id",
                new MapSqlParameterSource("r", reason).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.contextId()).addValue("id", id));
        audit.record(new AuditService.Command(TemplateSupport.MODULE, "DOCUMENT_VOID", "IssuedDocument", id, scope.organizationId(), cur.branchId(), Map.of("reason", reason)));
        return get(scope, id);
    }

    /** Verificación pública por código: solo estado, tipo, número, fecha y nombre abreviado — sin exponer datos completos. */
    @Transactional(readOnly = true)
    public TemplateDtos.VerificationResult verify(String codeRaw) {
        String code = codeRaw == null ? "" : codeRaw.trim().toUpperCase();
        if (code.length() < 6 || code.length() > 16) {
            return new TemplateDtos.VerificationResult(false, null, null, null, null, null, null, null);
        }
        List<TemplateDtos.VerificationResult> rows = jdbc.query("select d.status, d.type, d.document_no, d.issued_at, d.snapshot::text, o.name, b.name from issued_document d"
                + " join organization o on o.id = d.organization_id join branch b on b.id = d.branch_id where d.verification_code = :c",
                new MapSqlParameterSource("c", code), (rs, i) -> {
                    Map<String, String> snap = readSnapshot(rs.getString(5));
                    String primary = TemplateSupport.catalogOf(rs.getString(2)).primary();
                    String holderRaw = primary == null ? null : snap.get(primary);
                    return new TemplateDtos.VerificationResult(true, rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant(),
                            holderRaw == null ? null : abbreviate(holderRaw), rs.getString(6), rs.getString(7));
                });
        return rows.isEmpty() ? new TemplateDtos.VerificationResult(false, null, null, null, null, null, null, null) : rows.get(0);
    }

    /** Bytes del PDF ya emitido (para descarga desde el panel admin). */
    @Transactional(readOnly = true)
    public FileStorageService.StoredFile pdf(AccessScope scope, UUID id) {
        TemplateDtos.IssuedView v = get(scope, id);
        String key = jdbc.queryForObject("select pdf_ref from issued_document where id = :id", new MapSqlParameterSource("id", id), String.class);
        return storage.get(key).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** [X] exporta el listado filtrado a XLSX (sin generador externo, mismo {@code XlsxWriter} de los demás módulos). */
    @Transactional(readOnly = true)
    public byte[] exportXlsx(AccessScope scope, TemplateDtos.IssuedSearch req) {
        TemplateDtos.IssuedSearch full = new TemplateDtos.IssuedSearch(req == null ? null : req.filters(), new pe.dcs.app.util.pagination.PaginationRequest());
        full.pagination().setSize(5000);
        List<TemplateDtos.IssuedView> rows = search(scope, full).getContent();
        List<List<Object>> data = new java.util.ArrayList<>();
        for (TemplateDtos.IssuedView r : rows) {
            data.add(java.util.Arrays.asList(r.documentNo(), r.type(), r.branchName(), r.status(), r.verificationCode(), r.isDuplicate() ? "SI" : "NO", r.issuedAt().toString()));
        }
        return pe.dcs.app.shared.export.XlsxWriter.write("Documentos emitidos",
                List.of("Número", "Tipo", "Sede", "Estado", "Código", "Reimpreso", "Emitido"), data);
    }

    // ---------------------------------------------------------------- utilidades

    @SuppressWarnings("unchecked")
    private void resolveImageBytes(Map<String, Object> design, UUID signatoryId) {
        Object elementsObj = design.get("elements");
        if (!(elementsObj instanceof List<?> elements)) {
            return;
        }
        byte[] sigBytes = signatories.signatureBytes(signatoryId);
        for (Object eo : elements) {
            if (eo instanceof Map<?, ?> raw) {
                Map<String, Object> el = (Map<String, Object>) raw;
                String type = String.valueOf(el.getOrDefault("type", "")).toUpperCase();
                if ("SIGNATURE".equals(type)) {
                    el.put("_bytes", sigBytes);
                } else if ("IMAGE".equals(type)) {
                    Object key = el.get("key");
                    if (key != null) {
                        storage.get(String.valueOf(key)).ifPresent(f -> el.put("_bytes", f.data()));
                    }
                }
            }
        }
    }

    private static String prefix(String type) {
        return switch (type) {
            case "DONATION_CERTIFICATE" -> "DON";
            case "EVENT_TICKET" -> "TIC";
            case "EVENT_PARTICIPATION" -> "PAR";
            case "RECEIPT" -> "REC";
            case "PAYSLIP" -> "BOL";
            case "EMPLOYMENT_LETTER" -> "CAR";
            default -> "DOC";
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

    private String writeJson(Map<String, Object> m) {
        try {
            return mapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> readSnapshot(String json) {
        try {
            Map<String, Object> m = mapper.readValue(json, MAP);
            Map<String, String> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(k, v == null ? "" : String.valueOf(v)));
            return out;
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private TemplateDtos.IssuedView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp v = rs.getTimestamp("voided_at");
        return new TemplateDtos.IssuedView((UUID) rs.getObject("id"), (UUID) rs.getObject("branch_id"), rs.getString("branch_name"), rs.getString("type"),
                (UUID) rs.getObject("template_id"), rs.getInt("template_version"), rs.getString("subject_type"), (UUID) rs.getObject("subject_id"),
                rs.getString("document_no"), rs.getString("verification_code"), rs.getString("status"), rs.getString("void_reason"), rs.getBoolean("is_duplicate"),
                rs.getTimestamp("issued_at").toInstant(), (UUID) rs.getObject("issued_by"), rs.getString("issued_by_name"));
    }
}
