package pe.dcs.app.features.facility.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.dcs.app.features.facility.dto.FacilityDtos;
import pe.dcs.app.features.facility.service.FacilitySupport;
import pe.dcs.app.features.facility.service.InventoryImportService;
import pe.dcs.app.features.facility.service.InventoryItemService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** M16 · Ítems de inventario (INVENTORY): activos y consumibles, más importación masiva [I]. */
@RestController
@RequestMapping("/api/v1/admin/inventory/items")
@RequiredArgsConstructor
public class AdminInventoryItemController {

    private static final String MODULE = FacilitySupport.MOD_INVENTORY;
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final InventoryItemService service;
    private final InventoryImportService imports;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<FacilityDtos.ItemView>> search(@RequestBody(required = false) FacilityDtos.ItemSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<FacilityDtos.ItemView> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<FacilityDtos.ItemView> create(@RequestBody FacilityDtos.ItemRequest req) {
        return new ApiResponse<>(201, "ok.common.created", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<FacilityDtos.ItemView> update(@PathVariable UUID id, @RequestBody FacilityDtos.ItemRequest req) {
        return new ApiResponse<>(200, "ok.common.updated", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/{id}/retire")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<FacilityDtos.ItemView> retire(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body) {
        return new ApiResponse<>(200, "ok.common.statusChanged", service.retire(resolver.actor(), resolver.current(), id, body == null ? null : body.get("note")));
    }

    @PostMapping("/{id}/transfer")
    @ModuleAccess(module = MODULE, action = Action.T)
    public ApiResponse<FacilityDtos.ItemView> transfer(@PathVariable UUID id, @RequestBody FacilityDtos.TransferRequest req) {
        java.math.BigDecimal qty = FacilitySupport.requiredAmount(req.quantity(), "cantidad");
        return new ApiResponse<>(200, "ok.inventory.transferred", service.transfer(resolver.actor(), resolver.current(), id, req.toBranchId(), qty, req.note()));
    }

    @DeleteMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.D)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(resolver.actor(), resolver.current(), id);
        return new ApiResponse<>(200, "ok.common.deleted", null);
    }

    // ---------------------------------------------------------------- importación masiva [I]

    @GetMapping("/import/template")
    @ModuleAccess(module = MODULE, action = Action.I)
    public ResponseEntity<byte[]> importTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("plantilla-inventario.xlsx", StandardCharsets.UTF_8).build().toString())
                .contentType(XLSX).body(imports.template());
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ModuleAccess(module = MODULE, action = Action.I)
    public ApiResponse<FacilityDtos.ImportSummary> importPreview(@RequestPart("file") MultipartFile file, @RequestParam UUID branchId,
                                                                  @RequestParam(required = false) String onDuplicate) {
        return new ApiResponse<>(200, "ok.common.sent", imports.preview(resolver.actor(), resolver.current(), file, branchId, onDuplicate));
    }

    @PostMapping("/import/{jobId}/confirm")
    @ModuleAccess(module = MODULE, action = Action.I)
    public ApiResponse<FacilityDtos.ImportSummary> importConfirm(@PathVariable UUID jobId) {
        return new ApiResponse<>(200, "ok.inventory.imported", imports.confirm(resolver.actor(), resolver.current(), jobId));
    }

    @GetMapping("/import/{jobId}")
    @ModuleAccess(module = MODULE, action = Action.I)
    public ApiResponse<FacilityDtos.ImportSummary> importJob(@PathVariable UUID jobId) {
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
