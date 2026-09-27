package pe.dcs.app.features.person.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.person.dto.PersonDtos;
import pe.dcs.app.features.person.service.ConsentService;
import pe.dcs.app.features.person.service.PersonAnonymizeService;
import pe.dcs.app.features.person.service.PersonExportService;
import pe.dcs.app.features.person.service.PersonImportService;
import pe.dcs.app.features.person.service.PersonMergeService;
import pe.dcs.app.features.person.service.PersonPhotoService;
import pe.dcs.app.features.person.service.PersonService;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.shared.vo.DocumentType;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * M06 · Personas (N2/N3). ORG_ADMIN ve todas las sedes; ORG_BRANCH_ADMIN y ORG_USER, las suyas. Acciones: V consultar, C crear,
 * E editar/trasladar/etiquetar, S cambiar estado, H ver y escribir datos reservados.
 */
@RestController
@RequestMapping("/api/v1/admin/persons")
@RequiredArgsConstructor
public class AdminPersonController {

    private static final String MODULE = "PERSON";

    private final PersonService service;
    private final AccessScopeResolver resolver;
    private final ConsentService consents;
    private final PersonPhotoService photos;
    private final PersonExportService exports;
    private final PersonMergeService merges;
    private final PersonAnonymizeService anonymizer;
    private final PersonImportService imports;
    private final Clock clock;

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<PersonDtos.Summary>> search(@RequestBody(required = false) PersonDtos.Search req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    /** Busca por documento antes de crear: existe (abrir), existe en otra sede (sin datos) o no existe. */
    @GetMapping("/lookup")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PersonDtos.Lookup> lookup(@RequestParam DocumentType docType, @RequestParam String doc) {
        return new ApiResponse<>(200, "ok.common.sent", service.lookupByDocument(resolver.current(), docType, doc));
    }

    /** Selector de personas activas del alcance (PersonPicker). */
    @GetMapping("/picker")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<PersonDtos.Picker>> picker(@RequestParam(required = false) String q, @RequestParam(defaultValue = "15") int limit) {
        return new ApiResponse<>(200, "ok.common.sent", service.picker(resolver.current(), q, limit));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PersonDtos.Response> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.actor(), resolver.current(), id));
    }

    @GetMapping("/{id}/history")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PersonDtos.History> history(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.history(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<PersonDtos.Response> create(@Valid @RequestBody PersonDtos.Request req) {
        return new ApiResponse<>(201, "ok.person.created", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<PersonDtos.Response> update(@PathVariable UUID id, @Valid @RequestBody PersonDtos.Request req) {
        return new ApiResponse<>(200, "ok.person.updated", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/{id}/status")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<PersonDtos.Response> changeStatus(@PathVariable UUID id, @Valid @RequestBody PersonDtos.StatusRequest req) {
        return new ApiResponse<>(200, "ok.person.statusChanged", service.changeStatus(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/{id}/branch")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<PersonDtos.Response> transfer(@PathVariable UUID id, @Valid @RequestBody PersonDtos.BranchRequest req) {
        return new ApiResponse<>(200, "ok.person.transferred", service.transfer(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/{id}/tags")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<PersonDtos.Response> tags(@PathVariable UUID id, @RequestBody PersonDtos.TagsRequest req) {
        return new ApiResponse<>(200, "ok.person.tagsUpdated", service.setTags(resolver.actor(), resolver.current(), id, req));
    }

    // ---------------------------------------------------------------- privacidad, foto y exportación

    @GetMapping("/{id}/consents")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PersonDtos.Consents> consents(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", consents.consents(resolver.current(), id));
    }

    @PutMapping("/{id}/consents/{purpose}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<PersonDtos.Consents> setConsent(@PathVariable UUID id, @PathVariable String purpose, @RequestBody PersonDtos.ConsentRequest req) {
        AccessScope scope = resolver.current();
        return new ApiResponse<>(200, "ok.person.consentUpdated", consents.set(scope, scope.personId(), id, purpose.toUpperCase(), req.granted()));
    }

    @GetMapping("/{id}/photo")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ResponseEntity<byte[]> photo(@PathVariable UUID id) {
        PersonPhotoService.Photo f = photos.get(resolver.current(), id);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePrivate()).contentType(MediaType.parseMediaType(f.contentType())).body(f.data());
    }

    @PostMapping(value = "/{id}/photo", consumes = "multipart/form-data")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<PersonDtos.Photo> uploadPhoto(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return new ApiResponse<>(200, "ok.person.photoUpdated", photos.put(resolver.actor(), resolver.current(), id, file));
    }

    @DeleteMapping("/{id}/photo")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<PersonDtos.Photo> deletePhoto(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.person.photoRemoved", photos.delete(resolver.actor(), resolver.current(), id));
    }

    /** Exporta la lista (con los mismos filtros de la búsqueda) a XLSX. */
    @PostMapping("/export")
    @ModuleAccess(module = MODULE, action = Action.X)
    public ResponseEntity<byte[]> export(@RequestBody(required = false) PersonDtos.Search req) {
        PersonService.ExportResult ex = service.exportList(resolver.current(), req);
        String name = "personas-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(clock.instant().atZone(ZoneOffset.UTC)) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .header("X-Export-Rows", String.valueOf(ex.rows()))
                .contentType(XLSX).body(ex.content());
    }

    /** Descarga todos los datos de una persona (JSON): solicitud de acceso a la información. */
    @GetMapping("/{id}/export")
    @ModuleAccess(module = MODULE, action = Action.X)
    public ResponseEntity<byte[]> exportPerson(@PathVariable UUID id) {
        PersonExportService.Export ex = exports.exportPerson(resolver.actor(), resolver.current(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(ex.filename(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_JSON).body(ex.content());
    }

    // ---------------------------------------------------------------- fusión, anonimización e importación (M06 parte 2B)

    /** Vista previa de fusionar la persona {@code id} (duplicada) dentro de {@code targetId} (la que se conserva). */
    @GetMapping("/{id}/merge/preview")
    @ModuleAccess(module = MODULE, action = Action.M)
    public ApiResponse<PersonDtos.MergePreview> mergePreview(@PathVariable UUID id, @RequestParam UUID targetId) {
        return new ApiResponse<>(200, "ok.common.sent", merges.preview(resolver.current(), id, targetId));
    }

    @PostMapping("/{id}/merge")
    @ModuleAccess(module = MODULE, action = Action.M)
    public ApiResponse<PersonDtos.Response> merge(@PathVariable UUID id, @RequestBody PersonDtos.MergeRequest req) {
        return new ApiResponse<>(200, "ok.person.merged", merges.merge(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/anonymize")
    @ModuleAccess(module = MODULE, action = Action.P)
    public ApiResponse<PersonDtos.Response> anonymize(@PathVariable UUID id, @Valid @RequestBody PersonDtos.AnonymizeRequest req) {
        return new ApiResponse<>(200, "ok.person.anonymized", anonymizer.anonymize(resolver.actor(), resolver.current(), id, req));
    }

    @GetMapping("/import/template")
    @ModuleAccess(module = MODULE, action = Action.I)
    public ResponseEntity<byte[]> importTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("plantilla-personas.xlsx", StandardCharsets.UTF_8).build().toString())
                .contentType(XLSX).body(imports.template());
    }

    /** Valida el archivo (CSV o XLSX) y devuelve el resumen; no crea nada hasta confirmar. */
    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ModuleAccess(module = MODULE, action = Action.I)
    public ApiResponse<PersonDtos.ImportSummary> importPreview(@RequestPart("file") MultipartFile file, @RequestParam(required = false) String onDuplicate,
                                                               @RequestParam(required = false) UUID branchId) {
        return new ApiResponse<>(200, "ok.common.sent", imports.preview(resolver.actor(), resolver.current(), file, onDuplicate, branchId));
    }

    @PostMapping("/import/{jobId}/confirm")
    @ModuleAccess(module = MODULE, action = Action.I)
    public ApiResponse<PersonDtos.ImportSummary> importConfirm(@PathVariable UUID jobId) {
        return new ApiResponse<>(200, "ok.person.imported", imports.confirm(resolver.actor(), resolver.current(), jobId));
    }

    @GetMapping("/import/{jobId}")
    @ModuleAccess(module = MODULE, action = Action.I)
    public ApiResponse<PersonDtos.ImportSummary> importJob(@PathVariable UUID jobId) {
        return new ApiResponse<>(200, "ok.common.sent", imports.get(resolver.current(), jobId));
    }

    @GetMapping("/import/{jobId}/errors")
    @ModuleAccess(module = MODULE, action = Action.I)
    public ResponseEntity<byte[]> importErrors(@PathVariable UUID jobId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("errores-importacion.xlsx", StandardCharsets.UTF_8).build().toString())
                .contentType(XLSX).body(imports.errorReport(resolver.current(), jobId));
    }
}
