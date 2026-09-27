package pe.dcs.app.features.volunteer.web;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.volunteer.dto.VolunteerDtos;
import pe.dcs.app.features.volunteer.service.ServicePlanService;
import pe.dcs.app.features.volunteer.service.ShiftAssignmentService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/** M11b · Planes de servicio, sus turnos y las asignaciones de cada turno. */
@RestController
@RequestMapping("/api/v1/admin/service-plans")
@ModuleAccess(module = "VOLUNTEER_SCHEDULING", action = Action.V)
@RequiredArgsConstructor
public class AdminServicePlanController {

    private final ServicePlanService plans;
    private final ShiftAssignmentService assignments;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    public ApiResponse<PageResponse<VolunteerDtos.PlanResponse>> search(@RequestBody(required = false) VolunteerDtos.PlanSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", plans.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    public ApiResponse<VolunteerDtos.PlanResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", plans.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VolunteerDtos.PlanResponse> create(@RequestBody VolunteerDtos.PlanRequest req) {
        return new ApiResponse<>(201, "ok.shift.planCreated", plans.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    public ApiResponse<VolunteerDtos.PlanResponse> update(@PathVariable UUID id, @RequestBody VolunteerDtos.PlanUpdate req) {
        return new ApiResponse<>(200, "ok.shift.planUpdated", plans.update(resolver.actor(), resolver.current(), id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        plans.delete(resolver.actor(), resolver.current(), id);
        return new ApiResponse<>(200, "ok.common.deleted", null);
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<VolunteerDtos.PlanResponse> publish(@PathVariable UUID id, @RequestBody(required = false) VolunteerDtos.PublishRequest req) {
        return new ApiResponse<>(200, "ok.shift.published", plans.publish(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<VolunteerDtos.PlanResponse> close(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.shift.closed", plans.close(resolver.actor(), resolver.current(), id));
    }

    @PostMapping("/export")
    @ModuleAccess(module = "VOLUNTEER_SCHEDULING", action = Action.X)
    public ResponseEntity<ByteArrayResource> export(@RequestBody(required = false) VolunteerDtos.PlanSearch req) {
        ServicePlanService.ExportResult r = plans.export(resolver.actor(), resolver.current(), req);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=turnos.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(new ByteArrayResource(r.content()));
    }

    // ---------------------------------------------------------------- turnos

    @PostMapping("/{id}/slots")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VolunteerDtos.PlanResponse> addSlot(@PathVariable UUID id, @RequestBody VolunteerDtos.SlotRequest req) {
        return new ApiResponse<>(201, "ok.shift.slotAdded", plans.addSlot(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/{id}/slots/{slotId}")
    public ApiResponse<VolunteerDtos.PlanResponse> updateSlot(@PathVariable UUID id, @PathVariable UUID slotId, @RequestBody VolunteerDtos.SlotRequest req) {
        return new ApiResponse<>(200, "ok.shift.slotUpdated", plans.updateSlot(resolver.actor(), resolver.current(), id, slotId, req));
    }

    @DeleteMapping("/{id}/slots/{slotId}")
    public ApiResponse<VolunteerDtos.PlanResponse> deleteSlot(@PathVariable UUID id, @PathVariable UUID slotId) {
        return new ApiResponse<>(200, "ok.common.deleted", plans.deleteSlot(resolver.actor(), resolver.current(), id, slotId));
    }

    @GetMapping("/{id}/slots/{slotId}/suggest")
    public ApiResponse<List<VolunteerDtos.Candidate>> suggest(@PathVariable UUID id, @PathVariable UUID slotId) {
        return new ApiResponse<>(200, "ok.common.sent", assignments.suggest(resolver.current(), id, slotId));
    }

    // ---------------------------------------------------------------- asignaciones

    @PostMapping("/{id}/slots/{slotId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VolunteerDtos.PlanResponse> assign(@PathVariable UUID id, @PathVariable UUID slotId, @RequestBody VolunteerDtos.AssignRequest req) {
        return new ApiResponse<>(201, "ok.shift.assigned", assignments.assign(resolver.actor(), resolver.current(), id, slotId, req));
    }

    @DeleteMapping("/{id}/slots/{slotId}/assignments/{assignmentId}")
    public ApiResponse<VolunteerDtos.PlanResponse> unassign(@PathVariable UUID id, @PathVariable UUID slotId, @PathVariable UUID assignmentId) {
        return new ApiResponse<>(200, "ok.common.deleted", assignments.unassign(resolver.actor(), resolver.current(), id, slotId, assignmentId));
    }

    @PostMapping("/{id}/slots/{slotId}/assignments/{assignmentId}/confirm")
    public ApiResponse<VolunteerDtos.PlanResponse> confirm(@PathVariable UUID id, @PathVariable UUID slotId, @PathVariable UUID assignmentId) {
        return new ApiResponse<>(200, "ok.shift.confirmed", assignments.confirm(resolver.actor(), resolver.current(), id, slotId, assignmentId));
    }

    @PostMapping("/{id}/slots/{slotId}/assignments/{assignmentId}/decline")
    public ApiResponse<VolunteerDtos.PlanResponse> decline(@PathVariable UUID id, @PathVariable UUID slotId, @PathVariable UUID assignmentId,
                                                           @RequestBody(required = false) VolunteerDtos.DecideRequest req) {
        return new ApiResponse<>(200, "ok.shift.declined", assignments.decline(resolver.actor(), resolver.current(), id, slotId, assignmentId, req));
    }

    @PostMapping("/{id}/slots/{slotId}/assignments/{assignmentId}/serve")
    public ApiResponse<VolunteerDtos.PlanResponse> serve(@PathVariable UUID id, @PathVariable UUID slotId, @PathVariable UUID assignmentId,
                                                         @RequestBody VolunteerDtos.ServeRequest req) {
        return new ApiResponse<>(200, "ok.shift.served", assignments.serve(resolver.actor(), resolver.current(), id, slotId, assignmentId, req));
    }
}
