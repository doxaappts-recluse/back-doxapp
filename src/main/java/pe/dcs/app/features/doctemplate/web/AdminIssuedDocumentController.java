package pe.dcs.app.features.doctemplate.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.doctemplate.dto.TemplateDtos;
import pe.dcs.app.features.doctemplate.service.IssuedDocumentService;
import pe.dcs.app.features.doctemplate.service.TemplateSupport;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.shared.storage.FileStorageService;
import pe.dcs.app.util.ApiResponse;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/** M18 · Documentos emitidos ({@code DOC_TEMPLATES}): emisión, reimpresión, anulación y descarga del PDF. */
@RestController
@RequestMapping("/api/v1/admin/issued-documents")
@RequiredArgsConstructor
public class AdminIssuedDocumentController {

    private static final String MODULE = TemplateSupport.MODULE;

    private final IssuedDocumentService service;
    private final AccessScopeResolver resolver;
    private final Clock clock;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<pe.dcs.app.util.pagination.PageResponse<TemplateDtos.IssuedView>> search(@RequestBody(required = false) TemplateDtos.IssuedSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<TemplateDtos.IssuedView> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @GetMapping("/{id}/pdf")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        FileStorageService.StoredFile f = service.pdf(resolver.current(), id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(f.data());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.I)
    public ApiResponse<TemplateDtos.IssuedView> issue(@RequestBody TemplateDtos.IssueRequest req) {
        return new ApiResponse<>(201, "ok.document.issued", service.issue(resolver.actor(), resolver.current(), req));
    }

    @PostMapping("/{id}/reissue")
    @ModuleAccess(module = MODULE, action = Action.I)
    public ApiResponse<TemplateDtos.IssuedView> reissue(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.document.reissued", service.reissue(resolver.actor(), resolver.current(), id));
    }

    @PutMapping("/{id}/void")
    @ModuleAccess(module = MODULE, action = Action.Y)
    public ApiResponse<TemplateDtos.IssuedView> voidDocument(@PathVariable UUID id, @RequestBody TemplateDtos.VoidRequest req) {
        return new ApiResponse<>(200, "ok.document.voided", service.voidDocument(resolver.actor(), resolver.current(), id, req.reason()));
    }

    @PostMapping("/export")
    @ModuleAccess(module = MODULE, action = Action.X)
    public ResponseEntity<byte[]> export(@RequestBody(required = false) TemplateDtos.IssuedSearch req) {
        byte[] bytes = service.exportXlsx(resolver.current(), req);
        String name = "documentos-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(clock.instant().atZone(ZoneOffset.UTC)) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(bytes);
    }
}
