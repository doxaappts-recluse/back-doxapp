package pe.dcs.app.features.ministry.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.ministry.dto.MinistryDtos;
import pe.dcs.app.features.ministry.service.BranchMinistryService;
import pe.dcs.app.features.ministry.service.MinistryJoinService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/** M11 · Ministerios de la sede: activar (C), liderazgo (E), estado (S) y solicitudes de ingreso (C pedir, A decidir). */
@RestController
@RequestMapping("/api/v1/admin/branch-ministries")
@ModuleAccess(module = "MINISTRY", action = Action.V)
@RequiredArgsConstructor
public class AdminBranchMinistryController {

    private final BranchMinistryService service;
    private final MinistryJoinService joins;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    public ApiResponse<PageResponse<MinistryDtos.BranchMinistryResponse>> search(@RequestBody(required = false) MinistryDtos.BranchMinistrySearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    public ApiResponse<MinistryDtos.BranchMinistryResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MinistryDtos.BranchMinistryResponse> create(@RequestBody MinistryDtos.BranchMinistryRequest req) {
        return new ApiResponse<>(201, "ok.ministry.branchActivated", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}/leader")
    public ApiResponse<MinistryDtos.BranchMinistryResponse> leader(@PathVariable UUID id, @RequestBody MinistryDtos.LeaderRequest req) {
        return new ApiResponse<>(200, "ok.ministry.leaderSaved", service.setLeader(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<MinistryDtos.BranchMinistryResponse> activate(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.ministry.activated", service.setStatus(resolver.actor(), resolver.current(), id, true));
    }

    @PostMapping("/{id}/inactivate")
    public ApiResponse<MinistryDtos.BranchMinistryResponse> inactivate(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.ministry.inactivated", service.setStatus(resolver.actor(), resolver.current(), id, false));
    }

    // ---------------------------------------------------------------- solicitudes de ingreso

    @GetMapping("/{id}/join-requests")
    public ApiResponse<List<MinistryDtos.JoinRequestRow>> pending(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", joins.pending(resolver.current(), id));
    }

    @PostMapping("/{id}/join-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MinistryDtos.JoinRequestRow> requestJoin(@PathVariable UUID id, @RequestBody MinistryDtos.JoinRequest req) {
        return new ApiResponse<>(201, "ok.ministry.joinRequested", joins.request(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/join-requests/{requestId}/approve")
    public ApiResponse<Void> approve(@PathVariable UUID id, @PathVariable UUID requestId, @RequestBody(required = false) MinistryDtos.ReasonRequest req) {
        joins.decide(resolver.actor(), resolver.current(), id, requestId, true, req == null ? null : req.reason());
        return new ApiResponse<>(200, "ok.common.approved", null);
    }

    @PostMapping("/{id}/join-requests/{requestId}/reject")
    public ApiResponse<Void> reject(@PathVariable UUID id, @PathVariable UUID requestId, @RequestBody(required = false) MinistryDtos.ReasonRequest req) {
        joins.decide(resolver.actor(), resolver.current(), id, requestId, false, req == null ? null : req.reason());
        return new ApiResponse<>(200, "ok.common.rejected", null);
    }
}
