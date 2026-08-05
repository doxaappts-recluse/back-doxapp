package pe.dcs.app.features.visibility.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.visibility.request.VisibilityRequestApproveRequest;
import pe.dcs.app.features.visibility.request.VisibilityRequestCreateRequest;
import pe.dcs.app.features.visibility.request.VisibilityRequestSearchRequest;
import pe.dcs.app.features.visibility.response.PersonBranchOptionResponse;
import pe.dcs.app.features.visibility.response.VisibilityRequestPersonResponse;
import pe.dcs.app.features.visibility.response.VisibilityRequestRowResponse;
import pe.dcs.app.features.visibility.service.VisibilityRequestService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/**
 * Solicitudes de visibilidad entre sedes sobre data histórica de
 * Membresía/Servicio Ministerial/Bautizo (ver DataAccessRule /
 * VisibilityGrant / VisibilityRequest).
 */
@RestController
@RequestMapping("/api/v1/visibility-requests")
@RequiredArgsConstructor
public class VisibilityRequestController {

    private final VisibilityRequestService service;

    @PostMapping("/incoming")
    public ApiResponse<PageResponse<VisibilityRequestRowResponse>> searchIncoming(
            @RequestBody VisibilityRequestSearchRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.solicitudesObtenidasCorrectamente",
                service.searchIncoming(request)
        );
    }

    @PostMapping("/outgoing")
    public ApiResponse<PageResponse<VisibilityRequestRowResponse>> searchOutgoing(
            @RequestBody VisibilityRequestSearchRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.solicitudesObtenidasCorrectamente",
                service.searchOutgoing(request)
        );
    }

    @GetMapping("/person-by-dni/{dni}")
    public ApiResponse<VisibilityRequestPersonResponse> findPersonByDni(
            @PathVariable String dni
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.personaEncontrada",
                service.findPersonByDni(dni)
        );
    }

    @GetMapping("/branches/{personId}")
    public ApiResponse<List<PersonBranchOptionResponse>> getPersonBranches(
            @PathVariable UUID personId
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.sedesAnterioresObtenidasCorrectamente",
                service.getPersonBranches(personId)
        );
    }

    @PostMapping("/request/{personId}")
    public ApiResponse<String> create(
            @PathVariable UUID personId,
            @Valid @RequestBody VisibilityRequestCreateRequest request
    ) {

        service.create(personId, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.solicitudVisibilidadEnviadaCorrectamente",
                "OK"
        );
    }

    @PostMapping("/approve/{requestId}")
    public ApiResponse<String> approve(
            @PathVariable UUID requestId,
            @RequestBody(required = false) VisibilityRequestApproveRequest request
    ) {

        service.approve(requestId, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.solicitudAprobadaCorrectamente",
                "OK"
        );
    }

    @PostMapping("/reject/{requestId}")
    public ApiResponse<String> reject(
            @PathVariable UUID requestId
    ) {

        service.reject(requestId);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.solicitudRechazadaCorrectamente",
                "OK"
        );
    }
}
