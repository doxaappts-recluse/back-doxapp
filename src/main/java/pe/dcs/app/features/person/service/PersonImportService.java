package pe.dcs.app.features.person.service;

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
import pe.dcs.app.features.organization.dto.AddressDto;
import pe.dcs.app.features.person.dto.PersonDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.export.XlsxWriter;
import pe.dcs.app.shared.imports.TabularReader;
import pe.dcs.app.shared.vo.DocumentType;
import pe.dcs.app.shared.vo.DocumentValidator;
import pe.dcs.app.util.Exceptions;

import java.io.IOException;
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
 * M06 · Importación masiva de personas (acción I) [V13]: descargar plantilla → subir CSV/XLSX (≤ 5 MB y ≤ 5000 filas) → validar
 * fila por fila → previsualizar (nuevas, se actualizan, se omiten, con error) → confirmar → informe de errores. Una fila con error
 * no aborta el lote. Un documento que ya existe se omite o actualiza según la opción. Reimportar el mismo archivo es idempotente:
 * las personas ya creadas aparecen como existentes.
 *
 * <p>El archivo se valida al subirlo y las filas normalizadas quedan guardadas en {@code import_job}; confirmar las aplica sin
 * volver a subir nada. Cada fila se guarda en su propia transacción.
 */
@Service
@RequiredArgsConstructor
public class PersonImportService {

    public static final int MAX_ROWS = 5000;
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final String KIND = "PERSON";
    private static final int SAMPLE = 100;
    private static final List<String> TEMPLATE = List.of("Tipo de documento (DNI/CE/PASAPORTE)", "Número de documento", "Nombres", "Apellidos",
            "Sexo (M/F)", "Fecha de nacimiento (AAAA-MM-DD)", "Estado civil", "Teléfono", "Correo", "Dirección", "Ocupación",
            "Sede (código o nombre)", "Autorización (SI/NO)");

    /** Fila ya validada y normalizada, tal como se guarda en el trabajo. */
    public record StoredRow(int row, String action, String document, String name, String message, PersonDtos.Request req, UUID personId, boolean consent) {
    }

    private record Branch(UUID id, String name, String code, String status) {
    }

    private final PersonService personService;
    private final ConsentService consents;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final Clock clock;
    private final PlatformTransactionManager txm;

    // ---------------------------------------------------------------- plantilla

    public byte[] template() {
        return XlsxWriter.write("Personas", TEMPLATE, List.of());
    }

    // ---------------------------------------------------------------- vista previa

    public PersonDtos.ImportSummary preview(AuthenticatedActor actor, AccessScope scope, MultipartFile file, String onDuplicate, UUID defaultBranch) {
        if (file == null || file.isEmpty()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "archivo");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new Exceptions("error.person.importTooLarge", HttpStatus.UNPROCESSABLE_ENTITY);
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
            if (e.tooLarge()) {
                throw new Exceptions("error.person.importTooLarge", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            throw new Exceptions("error.common.fileType", HttpStatus.BAD_REQUEST, "CSV, XLSX");
        }
        if (table.isEmpty()) {
            throw new Exceptions("error.person.importEmpty", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Map<String, Integer> cols = columns(table.get(0));
        List<String> missing = new ArrayList<>();
        for (String req : List.of("docType", "docNumber", "firstName", "lastName")) {
            if (!cols.containsKey(req)) {
                missing.add(switch (req) {
                    case "docType" -> "Tipo de documento";
                    case "docNumber" -> "Número de documento";
                    case "firstName" -> "Nombres";
                    default -> "Apellidos";
                });
            }
        }
        if (!missing.isEmpty()) {
            throw new Exceptions("error.person.importHeaders", HttpStatus.UNPROCESSABLE_ENTITY, String.join(", ", missing));
        }
        if (table.size() == 1) {
            throw new Exceptions("error.person.importEmpty", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Map<UUID, Branch> branches = new HashMap<>();
        Map<String, Branch> byKey = new HashMap<>();
        jdbc.query("select id, name, code, status from branch where organization_id = :o", new MapSqlParameterSource("o", scope.organizationId()), rs -> {
            Branch b = new Branch((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4));
            if (scope.canSeeBranch(b.id())) {
                branches.put(b.id(), b);
                byKey.put(fold(b.name()), b);
                if (b.code() != null) {
                    byKey.put(fold(b.code()), b);
                }
            }
        });
        Branch fallback = null;
        if (defaultBranch != null) {
            fallback = branches.get(defaultBranch);
            if (fallback == null) {
                throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
            }
        }

        List<StoredRow> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, Integer> firstRow = new HashMap<>();
        for (int i = 1; i < table.size(); i++) {
            int line = i + 1;                                                     // número de fila en la hoja (1 = cabecera)
            List<String> cells = table.get(i);
            String doc = cell(cells, cols, "docNumber");
            String name = (cell(cells, cols, "firstName") + " " + cell(cells, cols, "lastName")).trim();
            String docLabel = (cell(cells, cols, "docType") + " " + doc).trim();
            try {
                boolean consent = yes(cell(cells, cols, "consent"));
                Branch branch = fallback;
                String branchText = cell(cells, cols, "branch");
                if (!branchText.isBlank()) {
                    branch = byKey.get(fold(branchText));
                    if (branch == null) {
                        throw new Exceptions("error.person.importBranch", HttpStatus.BAD_REQUEST, branchText);
                    }
                }
                PersonDtos.Request req = request(cells, cols, branch == null ? null : branch.id());
                String key = req.docType() + ":" + req.docNumber();
                if (!seen.add(key)) {
                    throw new Exceptions("error.person.importDuplicateInFile", HttpStatus.BAD_REQUEST, firstRow.get(key));
                }
                firstRow.put(key, line);
                PersonDtos.Lookup found = personService.lookupByDocument(scope, req.docType(), req.docNumber());   // también valida el documento
                if (found.existsOtherBranch()) {
                    throw new Exceptions("error.person.existsOtherBranch", HttpStatus.CONFLICT);
                }
                if (found.exists()) {
                    if ("MERGED".equals(found.status())) {
                        throw new Exceptions("error.person.merged", HttpStatus.CONFLICT);
                    }
                    if (dup.equals("UPDATE")) {
                        personService.validateForImport(overlay(actor, scope, found.personId(), req));              // se valida la ficha ya combinada
                        rows.add(new StoredRow(line, "UPDATE", docLabel, name, null, req, found.personId(), consent));
                    } else {
                        rows.add(new StoredRow(line, "SKIP", docLabel, name, null, req, found.personId(), consent));
                    }
                } else {
                    personService.validateForImport(req);
                    if (branch == null) {
                        throw new Exceptions("error.person.branchRequired", HttpStatus.BAD_REQUEST);
                    }
                    if (!"ACTIVE".equals(branch.status())) {
                        throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, branch.status());
                    }
                    rows.add(new StoredRow(line, "CREATE", docLabel, name, null, req, null, consent));
                }
            } catch (Exceptions e) {
                rows.add(new StoredRow(line, "ERROR", docLabel, name, e.getMessage(), null, null, false));
            }
        }

        UUID jobId = UUID.randomUUID();
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("onDuplicate", dup);
        options.put("branchId", defaultBranch);
        String name = file.getOriginalFilename() == null ? "archivo" : file.getOriginalFilename();
        if (name.length() > 200) {
            name = name.substring(name.length() - 200);
        }
        PersonDtos.ImportSummary summary = summarize(jobId, "PREVIEW", name, dup, rows);
        String finalName = name;
        new TransactionTemplate(txm).executeWithoutResult(st -> jdbc.update("""
                insert into import_job (id, organization_id, kind, file_name, file_hash, options, rows, summary, status, created_at, created_by)
                values (:id, :o, :k, :n, :h, cast(:opt as jsonb), cast(:rows as jsonb), cast(:sum as jsonb), 'PREVIEW', :at, :by)""",
                new MapSqlParameterSource("id", jobId).addValue("o", scope.organizationId()).addValue("k", KIND).addValue("n", finalName)
                        .addValue("h", sha256(data)).addValue("opt", json(options)).addValue("rows", json(rows)).addValue("sum", json(counts(rows)))
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId())));
        return summary;
    }

    // ---------------------------------------------------------------- confirmación

    /** Aplica las filas validadas. Sin transacción global: cada persona se guarda sola y una falla no afecta a las demás. */
    public PersonDtos.ImportSummary confirm(AuthenticatedActor actor, AccessScope scope, UUID jobId) {
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
                    id = personService.create(actor, scope, r.req()).id();
                } else {
                    id = r.personId();
                    personService.update(actor, scope, id, overlay(actor, scope, id, r.req()));
                }
                if (r.consent()) {
                    consents.grantDefaults(scope.organizationId(), id, "IMPORT", scope.personId());
                }
                out.add(new StoredRow(r.row(), r.action(), r.document(), r.name(), null, null, id, r.consent()));
            } catch (Exceptions e) {
                out.add(new StoredRow(r.row(), "ERROR", r.document(), r.name(), e.getMessage(), null, null, false));
            } catch (RuntimeException e) {
                out.add(new StoredRow(r.row(), "ERROR", r.document(), r.name(), pe.dcs.app.util.MessageSourceHolder.resolve("error.person.importRowFailed"), null, null, false));
            }
        }
        PersonDtos.ImportSummary summary = summarize(jobId, "DONE", job.fileName, job.onDuplicate, out);
        Map<String, Integer> c = counts(out);
        new TransactionTemplate(txm).executeWithoutResult(st -> {
            jdbc.update("update import_job set status = 'DONE', rows = cast(:rows as jsonb), summary = cast(:sum as jsonb), confirmed_at = :at where id = :id",
                    new MapSqlParameterSource("rows", json(out)).addValue("sum", json(c)).addValue("at", Timestamp.from(clock.instant())).addValue("id", jobId));
            audit.record(new AuditService.Command("PERSON", "IMPORT", "Person", null, scope.organizationId(), null,
                    Map.of("jobId", jobId, "created", summary.create(), "updated", summary.update(), "skipped", summary.skip(), "errors", summary.error())));
        });
        return summary;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public PersonDtos.ImportSummary get(AccessScope scope, UUID jobId) {
        Job job = load(scope, jobId);
        return summarize(jobId, job.status, job.fileName, job.onDuplicate, job.rows);
    }

    /** Informe descargable con las filas que fallaron (al validar o al confirmar). */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public byte[] errorReport(AccessScope scope, UUID jobId) {
        Job job = load(scope, jobId);
        List<List<Object>> data = new ArrayList<>();
        for (StoredRow r : job.rows) {
            if (r.action().equals("ERROR")) {
                data.add(java.util.Arrays.asList(r.row(), r.document(), r.name(), r.message()));
            }
        }
        return XlsxWriter.write("Errores", List.of("Fila", "Documento", "Nombre", "Detalle"), data);
    }

    // ---------------------------------------------------------------- lectura de filas

    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        put("docType", "tipo de documento", "tipo documento", "tipo de doc", "tipo doc", "tipo");
        put("docNumber", "numero de documento", "numero documento", "nro de documento", "nro documento", "documento", "dni", "numero de doc");
        put("firstName", "nombres", "nombre");
        put("lastName", "apellidos", "apellido");
        put("sex", "sexo", "genero");
        put("birth", "fecha de nacimiento", "fecha nacimiento", "nacimiento");
        put("marital", "estado civil");
        put("phone", "telefono", "celular", "movil");
        put("email", "correo", "email", "correo electronico");
        put("address", "direccion");
        put("occupation", "ocupacion");
        put("branch", "sede");
        put("consent", "autorizacion", "consentimiento");
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

    private PersonDtos.Request request(List<String> cells, Map<String, Integer> cols, UUID branchId) {
        String type = docType(cell(cells, cols, "docType"));
        String doc = DocumentValidator.normalize(cell(cells, cols, "docNumber"));
        AddressDto address = cell(cells, cols, "address").isBlank() ? null : new AddressDto(cell(cells, cols, "address"), null, null, null, null, null);
        return new PersonDtos.Request(type == null ? null : DocumentType.valueOf(type), doc, cell(cells, cols, "firstName"), cell(cells, cols, "lastName"),
                sex(cell(cells, cols, "sex")), date(cell(cells, cols, "birth")), marital(cell(cells, cols, "marital")), blank(cell(cells, cols, "email")),
                blank(cell(cells, cols, "phone")), null, address, blank(cell(cells, cols, "occupation")), branchId, null, null, null, null, null, null);
    }

    private static String docType(String raw) {
        if (raw.isBlank()) {
            return null;
        }
        return switch (fold(raw)) {
            case "dni" -> "DNI";
            case "ce", "carne de extranjeria", "carnet de extranjeria" -> "CE";
            case "pasaporte", "passport", "pas" -> "PASSPORT";
            default -> throw new Exceptions("error.person.importValue", HttpStatus.BAD_REQUEST, "Tipo de documento", raw);
        };
    }

    private static String sex(String raw) {
        if (raw.isBlank()) {
            return null;
        }
        return switch (fold(raw)) {
            case "m", "h", "masculino", "male", "hombre", "varon" -> "MALE";
            case "f", "femenino", "female", "mujer" -> "FEMALE";
            default -> throw new Exceptions("error.person.importValue", HttpStatus.BAD_REQUEST, "Sexo", raw);
        };
    }

    private static String marital(String raw) {
        if (raw.isBlank()) {
            return null;
        }
        return switch (fold(raw)) {
            case "soltero", "soltera", "soltero(a)", "single" -> "SINGLE";
            case "casado", "casada", "casado(a)", "married" -> "MARRIED";
            case "conviviente", "common_law", "union libre" -> "COMMON_LAW";
            case "viudo", "viuda", "viudo(a)", "widowed" -> "WIDOWED";
            case "divorciado", "divorciada", "divorciado(a)", "divorced" -> "DIVORCED";
            case "separado", "separada", "separado(a)", "separated" -> "SEPARATED";
            default -> throw new Exceptions("error.person.importValue", HttpStatus.BAD_REQUEST, "Estado civil", raw);
        };
    }

    private static final List<DateTimeFormatter> DATES = List.of(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"), DateTimeFormatter.ofPattern("uuuu/M/d"));

    private static LocalDate date(String raw) {
        if (raw.isBlank()) {
            return null;
        }
        if (raw.matches("\\d{1,6}(\\.\\d+)?")) {                                   // fecha de Excel guardada como número (días desde 1899-12-30)
            try {
                return LocalDate.of(1899, 12, 30).plusDays((long) Double.parseDouble(raw));
            } catch (RuntimeException e) {
                throw new Exceptions("error.person.importValue", HttpStatus.BAD_REQUEST, "Fecha de nacimiento", raw);
            }
        }
        for (DateTimeFormatter f : DATES) {
            try {
                return LocalDate.parse(raw, f);
            } catch (DateTimeParseException ignored) {
                // se prueba el siguiente formato
            }
        }
        throw new Exceptions("error.person.importValue", HttpStatus.BAD_REQUEST, "Fecha de nacimiento", raw);
    }

    private static boolean yes(String raw) {
        return switch (fold(raw)) {
            case "si", "s", "yes", "y", "1", "true", "x" -> true;
            default -> false;
        };
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String fold(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    /** Actualiza solo lo que el archivo trae: lo demás de la ficha se conserva. */
    private PersonDtos.Request overlay(AuthenticatedActor actor, AccessScope scope, UUID id, PersonDtos.Request in) {
        PersonDtos.Response cur = personService.get(actor, scope, id);
        AddressDto a = cur.address();
        AddressDto address = in.address() == null ? a : new AddressDto(in.address().line(), a == null ? null : a.district(), a == null ? null : a.city(),
                a == null ? null : a.region(), a == null ? null : a.country(), a == null ? null : a.reference());
        return new PersonDtos.Request(DocumentType.valueOf(cur.docType()), cur.docNumber(), or(in.firstName(), cur.firstName()), or(in.lastName(), cur.lastName()),
                or(in.sex(), cur.sex()), in.birthDate() != null ? in.birthDate() : cur.birthDate(), or(in.maritalStatus(), cur.maritalStatus()),
                or(in.email(), cur.email()), or(in.phone(), cur.phone()), cur.whatsapp(), address, or(in.occupation(), cur.occupation()), null, cur.joinedAt(),
                cur.privateNotes(), cur.allergies(), null, cur.version(), null);
    }

    private static String or(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    // ---------------------------------------------------------------- trabajo guardado

    private record Job(String fileName, String status, String onDuplicate, List<StoredRow> rows) {
    }

    private Job load(AccessScope scope, UUID jobId) {
        List<Object[]> r = jdbc.query("select file_name, status, options::text, rows::text from import_job where id = :id and organization_id = :o and kind = :k",
                new MapSqlParameterSource("id", jobId).addValue("o", scope.organizationId()).addValue("k", KIND),
                (rs, i) -> new Object[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)});
        if (r.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        try {
            Map<String, Object> opt = mapper.readValue((String) r.get(0)[2], new TypeReference<>() {
            });
            List<StoredRow> rows = mapper.readValue((String) r.get(0)[3], new TypeReference<>() {
            });
            return new Job((String) r.get(0)[0], (String) r.get(0)[1], String.valueOf(opt.getOrDefault("onDuplicate", "SKIP")), rows);
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

    private PersonDtos.ImportSummary summarize(UUID jobId, String status, String fileName, String dup, List<StoredRow> rows) {
        Map<String, Integer> c = counts(rows);
        List<PersonDtos.ImportRowView> sample = new ArrayList<>();
        for (StoredRow r : rows) {
            if (r.action().equals("ERROR") && sample.size() < SAMPLE) {
                sample.add(new PersonDtos.ImportRowView(r.row(), r.action(), r.document(), r.name(), r.message()));
            }
        }
        for (StoredRow r : rows) {
            if (!r.action().equals("ERROR") && sample.size() < SAMPLE) {
                sample.add(new PersonDtos.ImportRowView(r.row(), r.action(), r.document(), r.name(), r.message()));
            }
        }
        return new PersonDtos.ImportSummary(jobId, status, fileName, dup, c.get("total"), c.get("CREATE"), c.get("UPDATE"), c.get("SKIP"), c.get("ERROR"),
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
