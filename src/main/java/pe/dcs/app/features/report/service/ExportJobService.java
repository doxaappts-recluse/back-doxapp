package pe.dcs.app.features.report.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.report.dto.ReportDtos;
import pe.dcs.app.features.report.spi.ReportProvider;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.export.XlsxWriter;
import pe.dcs.app.shared.storage.FileStorageService;
import pe.dcs.app.util.Exceptions;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * M20 · Trabajos de exportación. [D4] sin cola de trabajos en el proyecto: se genera en la misma petición
 * (síncrono) hasta 50 000 filas [V4]; el registro igual queda para el historial y la ventana de descarga de 24h [V8].
 */
@Service
@RequiredArgsConstructor
public class ExportJobService {

    private static final String SELECT = "select * from export_job ";

    private final NamedParameterJdbcTemplate jdbc;
    private final ReportCatalogService catalog;
    private final AuthorizationService authz;
    private final FileStorageService storage;
    private final Clock clock;

    @Transactional
    public UUID exportOrg(AuthenticatedActor actor, AccessScope scope, ReportDtos.ExportRequest req) {
        ReportProvider p = catalog.provider(req.code());
        Set<String> actions = authz.effectiveActions(actor, p.moduleCode());
        if (!actions.contains("X")) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);                                                        // [V11]
        }
        ReportDtos.RunFilters filters = catalog.validateScope(scope, ReportSupport.normalize(req.filters(), clock));
        ReportProvider.ReportResult r = p.run(scope, new ReportProvider.Filters(filters.branchIds(), filters.from(), filters.to(), java.util.Map.of()));
        return build(scope.organizationId(), "PERSON", scope.personId(), req.code(), actions.contains("H") ? r : maskFor(r, p), req.format());
    }

    /** Usado por {@link ScheduledReportService}: el "dueño" del export generado por una programación es el proceso del sistema, no una persona. */
    @Transactional
    public UUID storeSystemExport(UUID orgId, String code, ReportProvider.ReportResult r, String format) {
        return build(orgId, "STAFF", SYSTEM_OWNER, code, r, format);
    }

    @Transactional
    public UUID exportPlatform(AuthenticatedActor actor, ReportDtos.ExportRequest req) {
        var p = catalog.platformProvider(req.code());
        ReportDtos.RunFilters filters = ReportSupport.normalize(req.filters(), clock);
        ReportProvider.ReportResult r = p.run(new ReportProvider.Filters(null, filters.from(), filters.to(), java.util.Map.of()));
        return build(null, "STAFF", actor.contextId(), req.code(), r, req.format());
    }

    private static final UUID SYSTEM_OWNER = new UUID(0, 0);

    private ReportProvider.ReportResult maskFor(ReportProvider.ReportResult r, ReportProvider p) {
        if (p.sensitiveColumns().isEmpty()) {
            return r;
        }
        List<Integer> idx = new java.util.ArrayList<>();
        for (int i = 0; i < r.columns().size(); i++) {
            if (p.sensitiveColumns().contains(r.columns().get(i))) {
                idx.add(i);
            }
        }
        List<List<Object>> rows = new java.util.ArrayList<>();
        for (List<Object> row : r.rows()) {
            List<Object> copy = new java.util.ArrayList<>(row);
            idx.forEach(i -> copy.set(i, null));
            rows.add(copy);
        }
        return new ReportProvider.ReportResult(r.columns(), rows, r.summary());
    }

    private UUID build(UUID orgIdOrNull, String ownerType, UUID ownerId, String code, ReportProvider.ReportResult r, String formatIn) {
        if (r.rows().size() > ReportSupport.MAX_EXPORT_ROWS) {
            throw new Exceptions("error.report.tooManyRows", HttpStatus.UNPROCESSABLE_ENTITY);                                           // [V4]
        }
        String format = "CSV".equalsIgnoreCase(formatIn) ? "CSV" : "XLSX";                                                               // [D5] PDF tabular no implementado: cae a XLSX
        byte[] bytes = format.equals("CSV") ? toCsv(r) : XlsxWriter.write(code, r.columns(), r.rows());
        String key = "org/" + (orgIdOrNull == null ? "platform" : orgIdOrNull) + "/reports/" + UUID.randomUUID() + "." + format.toLowerCase();
        storage.put(key, bytes, format.equals("CSV") ? "text/csv" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        UUID id = UUID.randomUUID();
        Instant expiresAt = clock.instant().plus(Duration.ofHours(24));                                                                  // [V8]
        jdbc.update("insert into export_job (id, organization_id, owner_type, owner_id, code, filters, format, row_count, status, file_ref, expires_at, created_at)"
                        + " values (:id, :o, :ot, :oid, :c, cast(:f as jsonb), :fmt, :rc, 'READY', :key, :exp, :at)",
                new MapSqlParameterSource("id", id).addValue("o", orgIdOrNull).addValue("ot", ownerType).addValue("oid", ownerId).addValue("c", code)
                        .addValue("f", "{}").addValue("fmt", format).addValue("rc", r.rows().size()).addValue("key", key)
                        .addValue("exp", Timestamp.from(expiresAt)).addValue("at", Timestamp.from(clock.instant())));
        return id;
    }

    private byte[] toCsv(ReportProvider.ReportResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", r.columns())).append('\n');
        for (List<Object> row : r.rows()) {
            sb.append(String.join(",", row.stream().map(v -> v == null ? "" : "\"" + String.valueOf(v).replace("\"", "\"\"") + "\"").toList())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** [V8] la descarga se comparte por organización (no solo por quien la generó): un envío programado tiene varios destinatarios
     * que deben poder bajar el mismo archivo. "otro usuario/org" del spec se traduce aquí en "otra organización → 404". */
    @Transactional(readOnly = true)
    public List<ReportDtos.ExportJobView> list(AccessScope scope) {
        return jdbc.query(SELECT + "where organization_id = :o order by created_at desc limit 50", new MapSqlParameterSource("o", scope.organizationId()), (rs, i) -> map(rs));
    }

    @Transactional(readOnly = true)
    public FileStorageService.StoredFile download(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id).addValue("o", scope.organizationId());
        return downloadInternal(jdbc.query(SELECT + "where id = :id and organization_id = :o", ps, (rs, i) -> new Object[]{rs.getString("file_ref"), rs.getTimestamp("expires_at")}));
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.ExportJobView> listPlatform() {
        return jdbc.query(SELECT + "where organization_id is null order by created_at desc limit 50", new MapSqlParameterSource(), (rs, i) -> map(rs));
    }

    @Transactional(readOnly = true)
    public FileStorageService.StoredFile downloadPlatform(UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        return downloadInternal(jdbc.query(SELECT + "where id = :id and organization_id is null", ps, (rs, i) -> new Object[]{rs.getString("file_ref"), rs.getTimestamp("expires_at")}));
    }

    private FileStorageService.StoredFile downloadInternal(List<Object[]> rows) {
        if (rows.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Timestamp exp = (Timestamp) rows.get(0)[1];
        if (exp.toInstant().isBefore(clock.instant())) {
            throw new Exceptions("error.report.exportExpired", HttpStatus.GONE);                                                         // [V8]
        }
        return storage.get((String) rows.get(0)[0]).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private ReportDtos.ExportJobView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ReportDtos.ExportJobView((UUID) rs.getObject("id"), rs.getString("code"), rs.getString("format"), rs.getInt("row_count"),
                rs.getString("status"), rs.getString("error_message"), rs.getTimestamp("expires_at").toInstant(), rs.getTimestamp("created_at").toInstant());
    }
}
