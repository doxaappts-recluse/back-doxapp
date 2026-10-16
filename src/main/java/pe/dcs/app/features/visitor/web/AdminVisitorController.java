package pe.dcs.app.features.visitor.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.visitor.dto.VisitorDtos;
import pe.dcs.app.features.visitor.service.VisitorRulesService;
import pe.dcs.app.features.visitor.service.VisitorService;
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
import java.util.Map;
import java.util.UUID;

/** M07 · Visitantes (N2/N3, módulo contratable): V consultar, C registrar, E editar/asignar/contactar, S cambiar de etapa, D borrar un registro por error. */
@RestController
@RequestMapping("/api/v1/admin/visitors")
@RequiredArgsConstructor
public class AdminVisitorController {

    private static final String MODULE = "VISITOR";

    private final VisitorService service;
    private final VisitorRulesService rules;
    private final AccessScopeResolver resolver;
    private final Clock clock;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<VisitorDtos.Summary>> search(@RequestBody(required = false) VisitorDtos.Search req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    /** Exporta los casos (con los filtros de la lista) a XLSX. */
    @PostMapping("/export")
    @ModuleAccess(module = MODULE, action = Action.X)
    public ResponseEntity<byte[]> export(@RequestBody(required = false) VisitorDtos.Search req) {
        VisitorService.ExportResult ex = service.exportList(resolver.current(), req);
        String name = "visitantes-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(clock.instant().atZone(ZoneOffset.UTC)) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .header("X-Export-Rows", String.valueOf(ex.rows()))
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(ex.content());
    }

    @PostMapping("/funnel")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<VisitorDtos.Funnel> funnel(@RequestBody(required = false) VisitorDtos.FunnelRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.funnel(resolver.current(), req));
    }

    @GetMapping("/matches")
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<VisitorDtos.MatchResult> matches(@RequestParam(required = false) DocumentType docType, @RequestParam(required = false) String doc,
                                                        @RequestParam(required = false) String phone, @RequestParam(required = false) String email,
                                                        @RequestParam(required = false) UUID branchId) {
        return new ApiResponse<>(200, "ok.common.sent", service.matches(resolver.current(), docType, doc, phone, email, branchId));
    }

    @GetMapping("/rules")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<VisitorDtos.Rules> rules() {
        return new ApiResponse<>(200, "ok.common.sent", rules.get(resolver.current().organizationId()));
    }

    @PutMapping("/rules")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<VisitorDtos.Rules> updateRules(@RequestBody VisitorDtos.RulesRequest req) {
        return new ApiResponse<>(200, "ok.visitor.rulesUpdated", rules.update(resolver.actor(), resolver.current(), req));
    }

    /** Revisa ahora los plazos de la organización (el job lo hace cada hora). Devuelve cuántos avisos generó. */
    @PostMapping("/sla-check")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<Map<String, Integer>> slaCheck() {
        return new ApiResponse<>(200, "ok.common.sent", Map.of("alerts", service.alertOverdue(resolver.current().organizationId())));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<VisitorDtos.Response> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<VisitorDtos.Response> create(@RequestBody VisitorDtos.CreateRequest req) {
        return new ApiResponse<>(201, "ok.visitor.created", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<VisitorDtos.Response> update(@PathVariable UUID id, @RequestBody VisitorDtos.UpdateRequest req) {
        return new ApiResponse<>(200, "ok.visitor.updated", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/{id}/assign")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<VisitorDtos.Response> assign(@PathVariable UUID id, @RequestBody VisitorDtos.AssignRequest req) {
        return new ApiResponse<>(200, "ok.visitor.assigned", service.assign(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<VisitorDtos.Response> contact(@PathVariable UUID id, @RequestBody VisitorDtos.ContactRequest req) {
        return new ApiResponse<>(201, "ok.visitor.contacted", service.addContact(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/{id}/integrate")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<VisitorDtos.Response> integrate(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.visitor.integrated", service.integrate(resolver.actor(), resolver.current(), id));
    }

    @PutMapping("/{id}/convert")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<VisitorDtos.Response> convert(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.visitor.converted", service.convert(resolver.actor(), resolver.current(), id));
    }

    @PutMapping("/{id}/archive")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<VisitorDtos.Response> archive(@PathVariable UUID id, @RequestBody VisitorDtos.ArchiveRequest req) {
        return new ApiResponse<>(200, "ok.visitor.archived", service.archive(resolver.actor(), resolver.current(), id, req));
    }

    @DeleteMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.D)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(resolver.actor(), resolver.current(), id);
        return new ApiResponse<>(200, "ok.visitor.deleted", null);
    }
}
