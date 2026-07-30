package pe.dcs.app.features.ministry_assignment.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.ministry_assignment.request.MinistryAssignmentCreateRequest;
import pe.dcs.app.features.ministry_assignment.request.MinistryAssignmentUpdateRequest;
import pe.dcs.app.features.ministry_assignment.response.MinistryAssignmentGroupedResponse;
import pe.dcs.app.features.ministry_assignment.response.MinistryAssignmentResponse;
import pe.dcs.app.features.ministry_assignment.response.MinistryRoleSimpleResponse;
import pe.dcs.app.features.ministry_assignment.response.MinistrySimpleResponse;
import pe.dcs.app.features.ministry_assignment.service.MinistryAssignmentService;
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
            @RequestBody MinistryAssignmentCreateRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Servicio ministerial registrado correctamente",
                service.create(userId, request)
        );
    }

    @PutMapping("/update/{userId}/{assignmentId}")
    public ApiResponse<MinistryAssignmentResponse> update(
            @PathVariable UUID userId,
            @PathVariable UUID assignmentId,
            @RequestBody MinistryAssignmentUpdateRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Servicio ministerial actualizado correctamente",
                service.update(userId, assignmentId, request)
        );
    }

    @DeleteMapping("/delete/{userId}/{assignmentId}")
    public ApiResponse<String> delete(
            @PathVariable UUID userId,
            @PathVariable UUID assignmentId
    ) {

        service.delete(userId, assignmentId);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Servicio ministerial eliminado correctamente",
                "OK"
        );
    }

    @GetMapping("/getBy/{userId}")
    public ApiResponse<List<MinistryAssignmentGroupedResponse>> getByUser(
            @PathVariable UUID userId
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Servicio ministerial obtenido correctamente",
                service.getByUser(userId)
        );
    }

    @GetMapping("/getAll/ministries")
    public ApiResponse<List<MinistrySimpleResponse>> getAllMinistries() {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Ministerios obtenidos correctamente",
                service.getAllMinistries()
        );
    }

    @GetMapping("/getAll/roles")
    public ApiResponse<List<MinistryRoleSimpleResponse>> getAllRoles() {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Roles obtenidos correctamente",
                service.getAllRoles()
        );
    }

}
