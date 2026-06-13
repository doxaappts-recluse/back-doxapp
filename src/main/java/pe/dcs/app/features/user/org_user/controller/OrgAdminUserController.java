package pe.dcs.app.features.user.org_user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.user.org_user.request.OrgAdminCreateRequest;
import pe.dcs.app.features.user.org_user.request.OrgAdminUpdateRequest;
import pe.dcs.app.features.user.org_user.response.OrgAdminResponse;
import pe.dcs.app.features.user.org_user.service.OrgAdminUserService;
import pe.dcs.app.util.ApiResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/org")
@RequiredArgsConstructor
public class OrgAdminUserController {

    private final OrgAdminUserService service;

    @GetMapping("/admin/{idOrg}")
    public ApiResponse<OrgAdminResponse> getOrgAdmin(
            @PathVariable UUID idOrg
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "ORG_ADMIN retrieved successfully",
                service.getOrgAdmin(idOrg)
        );
    }

    @PostMapping("/admin/create")
    public ApiResponse<OrgAdminResponse> createOrgAdmin(
            @Valid @RequestBody OrgAdminCreateRequest request
    ) {
        service.createOrgAdmin(request);

        return new ApiResponse<>(
                HttpStatus.CREATED.value(),
                "ORG_ADMIN created successfully",
                null
        );
    }

    @PutMapping("/admin/update/{id}")
    public ApiResponse<OrgAdminResponse> updateOrgAdmin(
            @PathVariable UUID id,
            @Valid @RequestBody OrgAdminUpdateRequest request
    ) {
        service.updateOrgAdmin(id, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "ORG_ADMIN updated successfully",
                null
        );
    }
}
