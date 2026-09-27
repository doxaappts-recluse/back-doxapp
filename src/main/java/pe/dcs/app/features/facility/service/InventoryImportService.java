package pe.dcs.app.features.facility.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import pe.dcs.app.features.facility.dto.FacilityDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.export.XlsxWriter;
import pe.dcs.app.shared.imports.TabularReader;
import pe.dcs.app.util.Exceptions;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M16 [I] · Importación masiva de activos/consumibles [M16-T15]: plantilla → subir CSV/XLSX (≤5 MB, ≤1000 filas) → vista
 * previa fila por fila (nuevo / se actualiza / se omite / con error) → confirmar. Reimportar el mismo archivo es idempotente
 * por (sede, código): las filas ya creadas aparecen como existentes. Mismo patrón que {@code PersonImportService} (M06c).
 */
@Service
@RequiredArgsConstructor
public class InventoryImportService {

    public static final int MAX_ROWS = 1000;
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final String KIND = "INVENTORY";
    private static final int SAMPLE = 100;
    private static final List<String> TEMPLATE = List.of("Código", "Nombre", "Categoría", "Tipo (ASSET/CONSUMABLE)", "Unidad", "Cantidad inicial",
            "Stock mínimo", "Ubicación", "Fecha de adquisición (AAAA-MM-DD)", "Costo", "Condición", "Número de serie");

    public record StoredRow(int row, String action, String code, String name, String message, FacilityDtos.ItemRequest req, UUID itemId) {
    }

    private final InventoryItemService items;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final Clock clock;
    private final PlatformTransactionManager txm;

    public byte[] template() {
        return XlsxWriter.write("Inventario", TEMPLATE, List.of());
    }

    public FacilityDtos.ImportSummary preview(AuthenticatedActor actor, AccessScope scope, MultipartFile file, UUID branchId, String onDuplicate) {
        if (branchId == null || !scope.canSeeBranch(branchId)) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
        }
        if (file == null || file.isEmpty()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "archivo");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new Exceptions("error.common.tooLong", HttpStatus.UNPROCESSABLE_ENTITY, "archivo", MAX_BYTES);
        }
        String dup = onDuplicate == null || onDuplicate.isBlank() ? "SKIP" : onDuplicate.trim().toUpperCase(Locale.ROOT);
        if (!dup.equals("SKIP") && !dup.equals("UPDATE")) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "opción de duplicados");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new Exceptions("error.common.storage", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        List<List<String>> table;
        try {
            table = TabularReader.read(file.getOriginalFilename(), data, MAX_ROWS + 1);
        } catch (TabularReader.Unreadable e) {
            throw new Exceptions("error.common.fileType", HttpStatus.BAD_REQUEST, "CSV, XLSX");
        }
        if (table.size() < 2) {
            throw new Exceptions("error.common.invalid", HttpStatus.UNPROCESSABLE_ENTITY, "archivo");
        }
        Map<String, Integer> cols = columns(table.get(0));
        if (!cols.containsKey("code") || !cols.containsKey("name") || !cols.containsKey("kind")) {
            throw new Exceptions("error.common.invalid", HttpStatus.UNPROCESSABLE_ENTITY, "encabezados");
        }
        Set<String> existingCodes = new HashSet<>(jdbc.queryForList("select upper(code) from inventory_item where branch_id = :b",
                new MapSqlParameterSource("b", branchId), String.class));

        List<StoredRow> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 1; i < table.size(); i++) {
            int line = i + 1;
            List<String> cells = table.get(i);
            String code = cell(cells, cols, "code").toUpperCase();
            String name = cell(cells, cols, "name");
            try {
                if (code.isBlank()) {
                    throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "código");
                }
                if (!seen.add(code)) {
                    throw new Exceptions("error.common.duplicate", HttpStatus.BAD_REQUEST, "código");
                }
                FacilityDtos.ItemRequest req = request(cells, cols, branchId);
                boolean exists = existingCodes.contains(code);
                if (exists) {
                    rows.add(new StoredRow(line, dup.equals("UPDATE") ? "UPDATE" : "SKIP", code, name, null, req, findId(branchId, code)));
                } else {
                    rows.add(new StoredRow(line, "CREATE", code, name, null, req, null));
                }
            } catch (Exceptions e) {
                rows.add(new StoredRow(line, "ERROR", code, name, e.getMessage(), null, null));
            }
        }
        UUID jobId = UUID.randomUUID();
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("onDuplicate", dup);
        options.put("branchId", branchId);
        String fname = file.getOriginalFilename() == null ? "archivo" : file.getOriginalFilename();
        FacilityDtos.ImportSummary summary = summarize(jobId, "PREVIEW", fname, rows);
        new TransactionTemplate(txm).executeWithoutResult(st -> jdbc.update("""
                insert into import_job (id, organization_id, kind, file_name, file_hash, options, rows, summary, status, created_at, created_by)
                values (:id, :o, :k, :n, :h, cast(:opt as jsonb), cast(:rows as jsonb), cast(:sum as jsonb), 'PREVIEW', :at, :by)""",
                new MapSqlParameterSource("id", jobId).addValue("o", scope.organizationId()).addValue("k", KIND).addValue("n", fname)
                        .addValue("h", sha256(data)).addValue("opt", json(options)).addValue("rows", json(rows)).addValue("sum", json(counts(rows)))
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId())));
        return summary;
    }

    public FacilityDtos.ImportSummary confirm(AuthenticatedActor actor, AccessScope scope, UUID jobId) {
        Job job = load(scope, jobId);
        if (!"PREVIEW".equals(job.status)) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, job.status);
        }
        List<StoredRow> out = new ArrayList<>(job.rows.size());
        for (StoredRow r : job.rows) {
            if (!r.action().equals("CREATE") && !r.action().equals("UPDATE")) {
                out.add(r);
                continue;
            }
            try {
                UUID id;
                if (r.action().equals("CREATE")) {
                    id = items.create(actor, scope, r.req()).id();
                } else {
                    id = r.itemId();
                    items.update(actor, scope, id, r.req());
                }
                out.add(new StoredRow(r.row(), r.action(), r.code(), r.name(), null, null, id));
            } catch (Exceptions e) {
                out.add(new StoredRow(r.row(), "ERROR", r.code(), r.name(), e.getMessage(), null, null));
            } catch (RuntimeException e) {
                out.add(new StoredRow(r.row(), "ERROR", r.code(), r.name(), "error.common.storage", null, null));
            }
        }
        FacilityDtos.ImportSummary summary = summarize(jobId, "DONE", job.fileName, out);
        Map<String, Integer> c = counts(out);
        new TransactionTemplate(txm).executeWithoutResult(st -> {
            jdbc.update("update import_job set status = 'DONE', rows = cast(:rows as jsonb), summary = cast(:sum as jsonb), confirmed_at = :at where id = :id",
                    new MapSqlParameterSource("rows", json(out)).addValue("sum", json(c)).addValue("at", Timestamp.from(clock.instant())).addValue("id", jobId));
            audit.record(new AuditService.Command(FacilitySupport.MOD_INVENTORY, "IMPORT", "InventoryItem", null, scope.organizationId(), null,
                    Map.of("jobId", jobId, "created", summary.create(), "updated", summary.update(), "errors", summary.error())));
        });
        return summary;
    }

    public FacilityDtos.ImportSummary get(AccessScope scope, UUID jobId) {
        Job job = load(scope, jobId);
        return summarize(jobId, job.status, job.fileName, job.rows);
    }

    public byte[] errorReport(AccessScope scope, UUID jobId) {
        Job job = load(scope, jobId);
        List<List<Object>> data = new ArrayList<>();
        for (StoredRow r : job.rows) {
            if (r.action().equals("ERROR")) {
                data.add(List.of(r.row(), r.code() == null ? "" : r.code(), r.name() == null ? "" : r.name(), r.message() == null ? "" : r.message()));
            }
        }
        return XlsxWriter.write("Errores", List.of("Fila", "Código", "Nombre", "Detalle"), data);
    }

    // ---------------------------------------------------------------- lectura de filas

    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        put("code", "codigo");
        put("name", "nombre");
        put("category", "categoria");
        put("kind", "tipo");
        put("unit", "unidad");
        put("qty", "cantidad inicial", "cantidad");
        put("min", "stock minimo", "minimo");
        put("location", "ubicacion");
        put("acquired", "fecha de adquisicion", "fecha adquisicion");
        put("cost", "costo");
        put("condition", "condicion");
        put("serial", "numero de serie", "serie");
    }

    private static void put(String key, String... names) {
        for (String n : names) {
            ALIASES.put(n, key);
        }
    }

    private static Map<String, Integer> columns(List<String> header) {
        Map<String, Integer> out = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String h = fold(header.get(i).replaceAll("\\(.*?\\)", ""));
            String key = ALIASES.get(h);
            if (key != null) {
                out.putIfAbsent(key, i);
            }
        }
        return out;
    }

    private static String cell(List<String> cells, Map<String, Integer> cols, String key) {
        Integer i = cols.get(key);
        return i == null || i >= cells.size() || cells.get(i) == null ? "" : cells.get(i).trim();
    }

    private FacilityDtos.ItemRequest request(List<String> cells, Map<String, Integer> cols, UUID branchId) {
        String kindRaw = fold(cell(cells, cols, "kind"));
        String kind = switch (kindRaw) {
            case "asset", "activo" -> "ASSET";
            case "consumable", "consumible" -> "CONSUMABLE";
            default -> throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
        };
        String name = cell(cells, cols, "name");
        if (name.isBlank()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        return new FacilityDtos.ItemRequest(cell(cells, cols, "code"), name, blank(cell(cells, cols, "category")), kind, blank(cell(cells, cols, "unit")),
                blank(cell(cells, cols, "qty")), blank(cell(cells, cols, "min")), blank(cell(cells, cols, "location")), date(cell(cells, cols, "acquired")),
                blank(cell(cells, cols, "cost")), null, blank(cell(cells, cols, "condition")), blank(cell(cells, cols, "serial")), branchId, null);
    }

    private static final List<DateTimeFormatter> DATES = List.of(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("d/M/uuuu"));

    private static LocalDate date(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (DateTimeFormatter f : DATES) {
            try {
                return LocalDate.parse(raw, f);
            } catch (DateTimeParseException ignored) {
                // se prueba el siguiente formato
            }
        }
        return null;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String fold(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private UUID findId(UUID branchId, String code) {
        List<UUID> r = jdbc.queryForList("select id from inventory_item where branch_id = :b and upper(code) = :c",
                new MapSqlParameterSource("b", branchId).addValue("c", code), UUID.class);
        return r.isEmpty() ? null : r.get(0);
    }

    // ---------------------------------------------------------------- trabajo guardado

    private record Job(String fileName, String status, List<StoredRow> rows) {
    }

    private Job load(AccessScope scope, UUID jobId) {
        List<Object[]> r = jdbc.query("select file_name, status, rows::text from import_job where id = :id and organization_id = :o and kind = :k",
                new MapSqlParameterSource("id", jobId).addValue("o", scope.organizationId()).addValue("k", KIND),
                (rs, i) -> new Object[]{rs.getString(1), rs.getString(2), rs.getString(3)});
        if (r.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        try {
            List<StoredRow> rows = mapper.readValue((String) r.get(0)[2], new TypeReference<>() { });
            return new Job((String) r.get(0)[0], (String) r.get(0)[1], rows);
        } catch (IOException e) {
            throw new Exceptions("error.common.storage", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static Map<String, Integer> counts(List<StoredRow> rows) {
        Map<String, Integer> c = new LinkedHashMap<>();
        c.put("total", rows.size());
        for (String a : List.of("CREATE", "UPDATE", "SKIP", "ERROR")) {
            c.put(a, (int) rows.stream().filter(r -> r.action().equals(a)).count());
        }
        return c;
    }

    private FacilityDtos.ImportSummary summarize(UUID jobId, String status, String fileName, List<StoredRow> rows) {
        Map<String, Integer> c = counts(rows);
        List<FacilityDtos.ImportRowView> sample = new ArrayList<>();
        for (StoredRow r : rows) {
            if (r.action().equals("ERROR") && sample.size() < SAMPLE) {
                sample.add(new FacilityDtos.ImportRowView(r.row(), r.action(), r.code(), r.name(), r.message()));
            }
        }
        for (StoredRow r : rows) {
            if (!r.action().equals("ERROR") && sample.size() < SAMPLE) {
                sample.add(new FacilityDtos.ImportRowView(r.row(), r.action(), r.code(), r.name(), r.message()));
            }
        }
        return new FacilityDtos.ImportSummary(jobId, status, fileName, c.get("total"), c.get("CREATE"), c.get("UPDATE"), c.get("SKIP"), c.get("ERROR"),
                sample, rows.size() > sample.size());
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (IOException e) {
            throw new Exceptions("error.common.storage", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static String sha256(byte[] data) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
