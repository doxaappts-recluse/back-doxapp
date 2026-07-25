package pe.dcs.app.features.user.system_user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.user.shared.UserChangePasswordRequest;
import pe.dcs.app.features.user.system_user.request.UserSystemCreateRequest;
import pe.dcs.app.features.user.system_user.request.UserSystemListRequest;
import pe.dcs.app.features.user.system_user.request.UserSystemUpdateRequest;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.features.user.system_user.response.UserSystemResponse;
import pe.dcs.app.features.user.system_user.service.UserSystemService;
import pe.dcs.app.util.ApiResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/system-users")
@RequiredArgsConstructor
public class SystemUserController {

    private final UserSystemService service;

    // =====================================================
    // SEARCH
    // =====================================================

    @PostMapping("/search")
    public ApiResponse<PageResponse<UserSystemResponse>> search(
            @RequestBody UserSystemListRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Users retrieved successfully",
                service.findAllSystem(request)
        );
    }

    // =====================================================
    // GET BY ID
    // =====================================================

    @GetMapping("/get/{id}")
    public ApiResponse<UserSystemResponse> findById(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "User retrieved successfully",
                service.findById(id)
        );
    }

    // =====================================================
    // CREATE
    // =====================================================

    @PostMapping("/create")
    public ApiResponse<UserSystemResponse> create(
            @Valid
            @RequestBody UserSystemCreateRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.CREATED.value(),
                "User created successfully",
                service.create(request)
        );
    }

    // =====================================================
    // UPDATE
    // =====================================================

    @PutMapping("/update/{id}")
    public ApiResponse<UserSystemResponse> update(
            @PathVariable UUID id,
            @Valid
            @RequestBody UserSystemUpdateRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "User updated successfully",
                service.update(id, request)
        );
    }

    // =====================================================
    // ENABLE
    // =====================================================

    @PatchMapping("/{id}/enable")
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

    // =====================================================
    // DISABLE
    // =====================================================

    @PatchMapping("/{id}/disable")
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

    // =====================================================
    // CHANGE PASSWORD
    // =====================================================

    @PatchMapping("/{id}/change-password")
    public ApiResponse<String> changePassword(
            @PathVariable UUID id,
            @Valid
            @RequestBody UserChangePasswordRequest request
    ) {

        service.changePassword(id, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Password updated successfully",
                "OK"
        );
    }

    // =====================================================
    // DELETE
    // =====================================================

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(
            @PathVariable UUID id
    ) {

        service.delete(id);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "User deleted successfully",
                "OK"
        );
    }
}