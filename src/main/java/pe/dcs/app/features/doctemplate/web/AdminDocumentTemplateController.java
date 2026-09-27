package pe.dcs.app.features.doctemplate.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.dcs.app.features.doctemplate.dto.TemplateDtos;
import pe.dcs.app.features.doctemplate.service.DocumentTemplateService;
import pe.dcs.app.features.doctemplate.service.TemplateSupport;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;

import java.io.IOException;
import java.util.UUID;

/** M18 · Plantillas de la organización (N2, {@code DOC_TEMPLATES}). */
@RestController
@RequestMapping("/api/v1/admin/document-templates")
@RequiredArgsConstructor
public class AdminDocumentTemplateController {

    private static final String MODULE = TemplateSupport.MODULE;

    private final DocumentTemplateService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<TemplateDtos.TemplateView>> search(@RequestBody(required = false) TemplateDtos.TemplateSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<TemplateDtos.TemplateView> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @GetMapping("/variables/{type}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<TemplateDtos.VariableCatalog> variables(@PathVariable String type) {
        String t = TemplateSupport.type(type);
        var cat = TemplateSupport.catalogOf(t);
        return new ApiResponse<>(200, "ok.common.sent", new TemplateDtos.VariableCatalog(cat.common().stream().sorted().toList(),
                cat.specific().stream().sorted().toList(), cat.primary() == null ? java.util.List.of() : java.util.List.of("number", cat.primary())));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<TemplateDtos.TemplateView> create(@RequestBody TemplateDtos.TemplateRequest req, @RequestParam(required = false) UUID fromBaseId) {
        return new ApiResponse<>(201, "ok.template.saved", service.create(resolver.actor(), resolver.current(), req, fromBaseId));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<TemplateDtos.TemplateView> update(@PathVariable UUID id, @RequestBody TemplateDtos.TemplateRequest req) {
        return new ApiResponse<>(200, "ok.template.saved", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/preview")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<TemplateDtos.PreviewResult> preview(@PathVariable UUID id, @RequestBody TemplateDtos.PreviewRequest req) {
        TemplateDtos.TemplateView cur = service.get(resolver.current(), id);
        return new ApiResponse<>(200, "ok.common.sent", service.preview(resolver.current(), req, cur.type()));
    }

    @PostMapping("/{id}/publish")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<TemplateDtos.TemplateView> publish(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.template.published", service.publish(resolver.actor(), resolver.current(), id));
    }

    @PutMapping("/{id}/default")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<TemplateDtos.TemplateView> setDefault(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.template.defaultSet", service.setDefault(resolver.actor(), resolver.current(), id));
    }

    @DeleteMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.D)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.archiveOrDelete(resolver.actor(), resolver.current(), id);
        return new ApiResponse<>(200, "ok.template.archived", null);
    }

    @PostMapping(value = "/images", consumes = "multipart/form-data")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<TemplateDtos.ImageUploadResult> uploadImage(@RequestPart("file") MultipartFile file) {
        try {
            return new ApiResponse<>(200, "ok.common.sent", service.uploadImage(resolver.current(), file.getContentType(), file.getBytes()));
        } catch (IOException e) {
            throw new Exceptions("error.template.imageInvalid", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }
}
