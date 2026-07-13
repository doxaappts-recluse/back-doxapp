package pe.dcs.app.features.access_user;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/access-users")
@RequiredArgsConstructor
public class AccessUserController {

    /*private final AccessUserSearchService service;

    @PostMapping("/search")
    public ApiResponse<PageResponse<AccessUserSearchResponse>> search(
            @RequestBody AccessUserSearchRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Access users retrieved successfully",
                service.search(request)
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
    // CHANGE-PASSWORD
    // =====================================================
    @PatchMapping("/{id}/change-password")
    public ApiResponse<String> changePassword(
            @PathVariable UUID id,
            @Valid
            @RequestBody UserCredentialsRequest request
    ) {

        service.changePassword(id, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Password updated successfully",
                "OK"
        );
    }*/

}