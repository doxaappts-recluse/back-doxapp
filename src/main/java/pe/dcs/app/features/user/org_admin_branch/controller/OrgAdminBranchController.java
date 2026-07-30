package pe.dcs.app.features.user.org_admin_branch.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.user.org_admin_branch.request.OrgAdminBranchAddAccessRequest;
import pe.dcs.app.features.user.org_admin_branch.request.OrgAdminBranchCreateRequest;
import pe.dcs.app.features.user.org_admin_branch.request.OrgAdminBranchListRequest;
import pe.dcs.app.features.user.org_admin_branch.request.OrgAdminBranchUpdateRequest;
import pe.dcs.app.features.user.org_admin_branch.response.OrgAdminBranchDetailResponse;
import pe.dcs.app.features.user.org_admin_branch.response.OrgAdminBranchResponse;
import pe.dcs.app.features.user.org_admin_branch.service.OrgAdminBranchService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/org-admin-branches")
@RequiredArgsConstructor
public class OrgAdminBranchController {

    private final OrgAdminBranchService service;

    @PostMapping("/search")
    public ApiResponse<PageResponse<OrgAdminBranchResponse>> search(
            @RequestBody OrgAdminBranchListRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Users retrieved successfully",
                service.search(request)
        );

    }

    @GetMapping("/getById/{id}")
    public ApiResponse<OrgAdminBranchDetailResponse> findById(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Users found",
                service.findById(id)
        );

    }

    @PostMapping("/create/{id}")
    public ApiResponse<String> create(
            @Valid @RequestBody OrgAdminBranchCreateRequest request
    ) {
        service.create(request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "User created",
                null
        );

    }

    @PutMapping("/update/{id}")
    public ApiResponse<String> update(
            @PathVariable UUID id,
            @Valid @RequestBody OrgAdminBranchUpdateRequest request
    ) {
        service.update(id, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "User updated",
                null
        );

    }

    @PatchMapping("/enable/{id}")
    public ApiResponse<String> enable(
            @PathVariable UUID id
    ) {

        service.enable(id);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "User enabled successfully",
                "OK"
        );

    }

    @PatchMapping("/disable/{id}")
    public ApiResponse<String> disable(
            @PathVariable UUID id
    ) {

        service.disable(id);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "User disabled successfully",
                "OK"
        );
    }

    @PostMapping("/{id}/accesses")
    public ApiResponse<String> addAccess(
            @PathVariable UUID id,
            @Valid @RequestBody OrgAdminBranchAddAccessRequest request
    ) {

        service.addAccess(id, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Access added successfully",
                null
        );
    }

    @PatchMapping("/accesses/{accessId}/enable")
    public ApiResponse<String> enableAccess(
            @PathVariable UUID accessId
    ) {

        service.enableAccess(accessId);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Access enabled successfully",
                "OK"
        );
    }

    @PatchMapping("/accesses/{accessId}/disable")
    public ApiResponse<String> disableAccess(
            @PathVariable UUID accessId
    ) {

        service.disableAccess(accessId);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Access disabled successfully",
                "OK"
        );
    }

}
