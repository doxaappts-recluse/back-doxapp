package pe.dcs.app.features.user.org_user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.user.org_user.request.OrgUserCreateRequest;
import pe.dcs.app.features.user.org_user.request.OrgUserListRequest;
import pe.dcs.app.features.user.org_user.request.OrgUserUpdateRequest;
import pe.dcs.app.features.user.org_user.response.OrgUserCreateResponse;
import pe.dcs.app.features.user.org_user.response.OrgUserResponse;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.features.user.org_user.service.OrgUserService;
import pe.dcs.app.util.ApiResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/org-user")
@RequiredArgsConstructor
public class OrgUserController {

    private final OrgUserService userOrgService;

    @PostMapping("/search")
    public ApiResponse<PageResponse<OrgUserResponse>> search(
            @RequestBody OrgUserListRequest request
    ) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Users retrieved successfully",
                userOrgService.search(request)
        );
    }

    @PostMapping("/create")
    public ApiResponse<OrgUserResponse> create(@RequestBody OrgUserCreateRequest request) {
        userOrgService.create(request);

        return new ApiResponse<>(
                HttpStatus.CREATED.value(),
                "User created",
                null
        );
    }

    @PutMapping("/update/{id}")
    public ApiResponse<OrgUserResponse> update(
            @PathVariable UUID id,
            @RequestBody OrgUserUpdateRequest request
    ) {
        userOrgService.update(id, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "User updated",
                null
        );
    }

    @GetMapping("/get/{id}")
    public ApiResponse<OrgUserCreateResponse> getById(
            @PathVariable UUID id
    ) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "User retrieved",
                userOrgService.getById(id)
        );
    }
}