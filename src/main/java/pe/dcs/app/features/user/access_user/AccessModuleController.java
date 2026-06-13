package pe.dcs.app.features.user.access_user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.user.access_user.request.UserAccessUpsertRequest;
import pe.dcs.app.features.user.access_user.response.UserAccessConfigResponse;
import pe.dcs.app.features.user.access_user.service.AccessModuleUserService;
import pe.dcs.app.util.ApiResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/access-users-module")
@RequiredArgsConstructor
public class AccessModuleController {

    private final AccessModuleUserService service;

    @GetMapping("/{userId}/config")
    public ResponseEntity<ApiResponse<UserAccessConfigResponse>> getConfig(
            @PathVariable UUID userId
    ) {

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Access configuration loaded",
                        service.getConfig(userId)
                )
        );
    }

    @PostMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> create(
            @PathVariable UUID userId,
            @RequestBody UserAccessUpsertRequest request
    ) {

        service.upsertAccess(userId, request);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "User module access created successfully",
                        null
                )
        );
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> update(
            @PathVariable UUID userId,
            @RequestBody UserAccessUpsertRequest request
    ) {

        service.upsertAccess(userId, request);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "User module access updated successfully",
                        null
                )
        );
    }
}
