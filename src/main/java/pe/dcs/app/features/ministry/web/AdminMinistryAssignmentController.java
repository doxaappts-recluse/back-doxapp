package pe.dcs.app.features.ministry.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.ministry.dto.MinistryDtos;
import pe.dcs.app.features.ministry.service.MinistryAssignmentService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** M11 · Asignaciones de personas a cargos: C asignar, E terminar, X exportar. */
@RestController
@RequestMapping("/api/v1/admin/ministry-assignments")
@ModuleAccess(module = "MINISTRY", action = Action.V)
@RequiredArgsConstructor
public class AdminMinistryAssignmentController {

    private final MinistryAssignmentService service;
    private final AccessScopeResolver resolver;
    private final Clock clock;

    @PostMapping("/search")
    public ApiResponse<PageResponse<MinistryDtos.AssignmentResponse>> search(@RequestBody(required = false) MinistryDtos.AssignmentSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MinistryDtos.AssignmentResponse> assign(@RequestBody MinistryDtos.AssignRequest req) {
        return new ApiResponse<>(201, "ok.ministry.assigned", service.assign(resolver.actor(), resolver.current(), req));
    }

    @PostMapping("/{id}/end")
    public ApiResponse<MinistryDtos.AssignmentResponse> end(@PathVariable UUID id, @RequestBody(required = false) MinistryDtos.EndRequest req) {
        return new ApiResponse<>(200, "ok.ministry.assignmentEnded", service.end(resolver.actor(), resolver.current(), id, req));
    }

    @GetMapping("/persons/{personId}")
    public ApiResponse<List<MinistryDtos.PersonMinistry>> ofPerson(@PathVariable UUID personId) {
        return new ApiResponse<>(200, "ok.common.sent", service.ofPerson(resolver.current(), personId));
    }

    @PostMapping("/export")
    @ModuleAccess(module = "MINISTRY", action = Action.X)
    public ResponseEntity<byte[]> export(@RequestBody(required = false) MinistryDtos.AssignmentSearch req) {
        MinistryAssignmentService.ExportResult ex = service.export(resolver.actor(), resolver.current(), req);
        String name = "ministerios-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(clock.instant().atZone(ZoneOffset.UTC)) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .header("X-Export-Rows", String.valueOf(ex.rows()))
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(ex.content());
    }
}
