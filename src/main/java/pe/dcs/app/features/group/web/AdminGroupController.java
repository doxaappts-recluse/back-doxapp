package pe.dcs.app.features.group.web;

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
import pe.dcs.app.features.group.dto.GroupDtos;
import pe.dcs.app.features.group.service.GroupHealthService;
import pe.dcs.app.features.group.service.GroupJoinService;
import pe.dcs.app.features.group.service.GroupMeetingService;
import pe.dcs.app.features.group.service.GroupRulesService;
import pe.dcs.app.features.group.service.GroupService;
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

/**
 * M10 · Grupos y células (módulo contratable SMALL_GROUP): V consultar, C crear y multiplicar, E editar integrantes y reuniones, D borrar borradores, S cambiar el estado,
 * A aprobar ingresos, X exportar. Los permisos finos de cada operación los comprueba el servicio.
 */
@RestController
@RequestMapping("/api/v1/admin/groups")
@ModuleAccess(module = "SMALL_GROUP", action = Action.V)
@RequiredArgsConstructor
public class AdminGroupController {

    private final GroupService service;
    private final GroupMeetingService meetings;
    private final GroupHealthService health;
    private final GroupJoinService joins;
    private final GroupRulesService rules;
    private final AccessScopeResolver resolver;
    private final Clock clock;

    // ---------------------------------------------------------------- grupos

    @PostMapping("/search")
    public ApiResponse<PageResponse<GroupDtos.GroupResponse>> search(@RequestBody(required = false) GroupDtos.Search req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    public ApiResponse<GroupDtos.GroupResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupDtos.GroupResponse> create(@RequestBody GroupDtos.GroupRequest req) {
        return new ApiResponse<>(201, "ok.group.created", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    public ApiResponse<GroupDtos.GroupResponse> update(@PathVariable UUID id, @RequestBody GroupDtos.GroupUpdate req) {
        return new ApiResponse<>(200, "ok.common.updated", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<GroupDtos.GroupResponse> activate(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.group.activated", service.activate(resolver.actor(), resolver.current(), id));
    }

    @PostMapping("/{id}/pause")
    public ApiResponse<GroupDtos.GroupResponse> pause(@PathVariable UUID id, @RequestBody(required = false) GroupDtos.ReasonRequest req) {
        return new ApiResponse<>(200, "ok.group.paused", service.pause(resolver.actor(), resolver.current(), id, req == null ? null : req.reason()));
    }

    @PostMapping("/{id}/resume")
    public ApiResponse<GroupDtos.GroupResponse> resume(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.group.resumed", service.resume(resolver.actor(), resolver.current(), id));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<GroupDtos.GroupResponse> close(@PathVariable UUID id, @RequestBody GroupDtos.ReasonRequest req) {
        return new ApiResponse<>(200, "ok.group.closed", service.close(resolver.actor(), resolver.current(), id, req == null ? null : req.reason()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(resolver.actor(), resolver.current(), id);
        return new ApiResponse<>(200, "ok.common.deleted", null);
    }

    @PostMapping("/{id}/multiply")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupDtos.MultiplyResult> multiply(@PathVariable UUID id, @RequestBody GroupDtos.MultiplyRequest req) {
        return new ApiResponse<>(201, "ok.group.multiplied", service.multiply(resolver.actor(), resolver.current(), id, req));
    }

    @GetMapping("/persons/{personId}")
    public ApiResponse<List<GroupDtos.PersonGroup>> ofPerson(@PathVariable UUID personId) {
        return new ApiResponse<>(200, "ok.common.sent", service.ofPerson(resolver.current(), personId));
    }

    @PostMapping("/export")
    @ModuleAccess(module = "SMALL_GROUP", action = Action.X)
    public ResponseEntity<byte[]> export(@RequestBody(required = false) GroupDtos.Search req) {
        GroupService.ExportResult ex = service.export(resolver.actor(), resolver.current(), req);
        String name = "grupos-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(clock.instant().atZone(ZoneOffset.UTC)) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .header("X-Export-Rows", String.valueOf(ex.rows()))
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(ex.content());
    }

    // ---------------------------------------------------------------- integrantes

    @GetMapping("/{id}/members")
    public ApiResponse<List<GroupDtos.MemberResponse>> members(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean includeLeft) {
        return new ApiResponse<>(200, "ok.common.sent", service.members(resolver.current(), id, includeLeft));
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupDtos.MemberResponse> addMember(@PathVariable UUID id, @RequestBody GroupDtos.MemberAdd req) {
        return new ApiResponse<>(201, "ok.group.memberAdded", service.addMember(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/{id}/members/{memberId}")
    public ApiResponse<GroupDtos.MemberResponse> changeRole(@PathVariable UUID id, @PathVariable UUID memberId, @RequestBody GroupDtos.MemberRole req) {
        return new ApiResponse<>(200, "ok.common.updated", service.changeRole(resolver.actor(), resolver.current(), id, memberId, req));
    }

    @DeleteMapping("/{id}/members/{memberId}")
    public ApiResponse<Void> removeMember(@PathVariable UUID id, @PathVariable UUID memberId) {
        service.removeMember(resolver.actor(), resolver.current(), id, memberId);
        return new ApiResponse<>(200, "ok.group.left", null);
    }

    // ---------------------------------------------------------------- reuniones y asistencia

    @PostMapping("/meetings/search")
    public ApiResponse<PageResponse<GroupDtos.MeetingResponse>> searchMeetings(@RequestBody(required = false) GroupDtos.MeetingSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", meetings.search(resolver.current(), req));
    }

    @GetMapping("/{id}/meetings/{meetingId}")
    public ApiResponse<GroupDtos.MeetingResponse> meeting(@PathVariable UUID id, @PathVariable UUID meetingId) {
        return new ApiResponse<>(200, "ok.common.sent", meetings.get(resolver.current(), id, meetingId));
    }

    @PostMapping("/{id}/meetings")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupDtos.MeetingCreated> createMeeting(@PathVariable UUID id, @RequestBody GroupDtos.MeetingRequest req) {
        return new ApiResponse<>(201, "ok.group.meetingCreated", meetings.create(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/{id}/meetings/{meetingId}")
    public ApiResponse<GroupDtos.MeetingResponse> updateMeeting(@PathVariable UUID id, @PathVariable UUID meetingId, @RequestBody GroupDtos.MeetingUpdate req) {
        return new ApiResponse<>(200, "ok.common.updated", meetings.update(resolver.actor(), resolver.current(), id, meetingId, req));
    }

    @PostMapping("/{id}/meetings/{meetingId}/cancel")
    public ApiResponse<GroupDtos.MeetingResponse> cancelMeeting(@PathVariable UUID id, @PathVariable UUID meetingId, @RequestBody GroupDtos.ReasonRequest req) {
        return new ApiResponse<>(200, "ok.group.meetingCancelled", meetings.cancel(resolver.actor(), resolver.current(), id, meetingId, req == null ? null : req.reason()));
    }

    @PostMapping("/{id}/meetings/{meetingId}/hold")
    public ApiResponse<GroupDtos.MeetingResponse> hold(@PathVariable UUID id, @PathVariable UUID meetingId, @RequestBody(required = false) GroupDtos.HoldRequest req) {
        return new ApiResponse<>(200, "ok.group.meetingHeld", meetings.hold(resolver.actor(), resolver.current(), id, meetingId, req));
    }

    @GetMapping("/{id}/meetings/{meetingId}/attendance")
    public ApiResponse<GroupDtos.AttendanceView> attendance(@PathVariable UUID id, @PathVariable UUID meetingId) {
        return new ApiResponse<>(200, "ok.common.sent", meetings.attendance(resolver.current(), id, meetingId));
    }

    @PostMapping("/{id}/meetings/{meetingId}/attendance")
    public ApiResponse<GroupDtos.AttendanceView> mark(@PathVariable UUID id, @PathVariable UUID meetingId, @RequestBody GroupDtos.AttendanceMark req) {
        return new ApiResponse<>(200, "ok.attendance.recorded", meetings.mark(resolver.actor(), resolver.current(), id, meetingId, req));
    }

    @DeleteMapping("/{id}/meetings/{meetingId}/attendance/{personId}")
    public ApiResponse<GroupDtos.AttendanceView> unmark(@PathVariable UUID id, @PathVariable UUID meetingId, @PathVariable UUID personId) {
        return new ApiResponse<>(200, "ok.common.updated", meetings.unmark(resolver.actor(), resolver.current(), id, meetingId, personId));
    }

    // ---------------------------------------------------------------- salud

    @GetMapping("/health")
    public ApiResponse<GroupDtos.HealthOverview> healthOverview(@RequestParam(required = false) Integer weeks) {
        return new ApiResponse<>(200, "ok.common.sent", health.overview(resolver.current(), weeks));
    }

    @GetMapping("/{id}/health")
    public ApiResponse<GroupDtos.Health> healthOf(@PathVariable UUID id, @RequestParam(required = false) Integer weeks) {
        return new ApiResponse<>(200, "ok.common.sent", health.group(resolver.current(), id, weeks));
    }

    // ---------------------------------------------------------------- solicitudes de ingreso

    @GetMapping("/{id}/join-requests")
    public ApiResponse<List<GroupDtos.JoinRequestRow>> pending(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", joins.pending(resolver.current(), id));
    }

    @PostMapping("/{id}/join-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupDtos.JoinRequestRow> requestJoin(@PathVariable UUID id, @RequestBody GroupDtos.JoinRequest req) {
        return new ApiResponse<>(201, "ok.group.joinRequested", joins.request(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/join-requests/{requestId}/approve")
    public ApiResponse<Void> approve(@PathVariable UUID id, @PathVariable UUID requestId, @RequestBody(required = false) GroupDtos.ReasonRequest req) {
        joins.decide(resolver.actor(), resolver.current(), id, requestId, true, req == null ? null : req.reason());
        return new ApiResponse<>(200, "ok.common.approved", null);
    }

    @PostMapping("/{id}/join-requests/{requestId}/reject")
    public ApiResponse<Void> reject(@PathVariable UUID id, @PathVariable UUID requestId, @RequestBody(required = false) GroupDtos.ReasonRequest req) {
        joins.decide(resolver.actor(), resolver.current(), id, requestId, false, req == null ? null : req.reason());
        return new ApiResponse<>(200, "ok.common.rejected", null);
    }

    // ---------------------------------------------------------------- reglas (administrador de la organización)

    @GetMapping("/rules")
    public ApiResponse<GroupDtos.Rules> rules() {
        return new ApiResponse<>(200, "ok.common.sent", rules.get(resolver.current().organizationId()));
    }

    @PutMapping("/rules")
    public ApiResponse<GroupDtos.Rules> saveRules(@RequestBody GroupDtos.RulesRequest req) {
        return new ApiResponse<>(200, "ok.group.rulesSaved", rules.update(resolver.actor(), resolver.current(), req));
    }
}
