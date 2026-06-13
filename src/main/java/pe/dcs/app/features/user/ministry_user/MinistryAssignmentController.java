package pe.dcs.app.features.user.ministry_user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.user.ministry_user.request.MinistryAssignmentRequest;
import pe.dcs.app.features.user.ministry_user.response.MinistryAssignmentResponse;
import pe.dcs.app.features.user.ministry_user.response.MinistryGroupedResponse;
import pe.dcs.app.features.user.ministry_user.response.MinistryOptionResponse;
import pe.dcs.app.features.user.ministry_user.response.MinistryRoleOptionResponse;
import pe.dcs.app.features.user.ministry_user.service.MinistryAssignmentService;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ministry-assignment")
@RequiredArgsConstructor
public class MinistryAssignmentController {

    private final MinistryAssignmentService service;

    @PostMapping("/create/{userId}")
    public ApiResponse<MinistryAssignmentResponse> create(
            @PathVariable UUID userId,
            @RequestBody MinistryAssignmentRequest request
    ) {
        service.create(userId, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Ministry assignment created successfully",
                null
        );
    }

    @PutMapping("/update/{userId}/{assignmentId}")
    public ApiResponse<MinistryAssignmentResponse> update(
            @PathVariable UUID userId,
            @PathVariable UUID assignmentId,
            @RequestBody MinistryAssignmentRequest request
    ) {
        service.update(
                userId,
                assignmentId,
                request
        );
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Ministry assignment updated successfully",
                null

        );
    }

    @DeleteMapping("/delete/{userId}/{assignmentId}")
    public ApiResponse<Void> delete(
            @PathVariable UUID userId,
            @PathVariable UUID assignmentId
    ) {

        service.delete(
                userId,
                assignmentId
        );

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Ministry assignment deleted successfully",
                null
        );
    }

    @GetMapping("/getBy/{userId}")
    public ApiResponse<List<MinistryGroupedResponse>>
    getServices(
            @PathVariable UUID userId
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Ministry assignments retrieved successfully",
                service.getServices(userId)
        );
    }

    @GetMapping("/getAll/ministries")
    public ApiResponse<List<MinistryOptionResponse>>
    getMinistries() {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Ministries retrieved successfully",
                service.getMinistries()
        );
    }

    @GetMapping("/getAll/roles")
    public ApiResponse<List<MinistryRoleOptionResponse>>
    getRoles() {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Ministry roles retrieved successfully",
                service.getRoles()
        );
    }
}