package pe.dcs.app.features.branch_transfer.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.branch_transfer.request.BranchTransferHistoryRequest;
import pe.dcs.app.features.branch_transfer.request.BranchTransferRequest;
import pe.dcs.app.features.branch_transfer.request.BranchTransferSearchRequest;
import pe.dcs.app.features.branch_transfer.response.BranchTransferContextResponse;
import pe.dcs.app.features.branch_transfer.response.BranchTransferHistoryResponse;
import pe.dcs.app.features.branch_transfer.response.BranchTransferSearchRowResponse;
import pe.dcs.app.features.branch_transfer.service.BranchTransferService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/**
 * Traslado de personas entre sedes de la misma organización:
 * listado con su sede actual, ver/trasladar a una persona
 * puntual, e historial de sedes de esa persona.
 */
@RestController
@RequestMapping("/api/v1/branch-transfer")
@RequiredArgsConstructor
public class BranchTransferController {

    private final BranchTransferService service;

    @PostMapping("/search")
    public ApiResponse<PageResponse<BranchTransferSearchRowResponse>> search(
            @RequestBody BranchTransferSearchRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.personasObtenidasCorrectamente",
                service.search(request)
        );
    }

    @GetMapping("/current/{personId}")
    public ApiResponse<BranchTransferContextResponse> getCurrent(
            @PathVariable UUID personId
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.sedeActualObtenidaCorrectamente",
                service.getCurrent(personId)
        );
    }

    @PostMapping("/history/{personId}")
    public ApiResponse<PageResponse<BranchTransferHistoryResponse>> history(
            @PathVariable UUID personId,
            @RequestBody BranchTransferHistoryRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.historialSedesObtenidoCorrectamente",
                service.history(personId, request)
        );
    }

    @PostMapping("/transfer/{personId}")
    public ApiResponse<String> transfer(
            @PathVariable UUID personId,
            @Valid @RequestBody BranchTransferRequest request
    ) {

        service.transfer(personId, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.personaTrasladadaCorrectamente",
                "OK"
        );
    }

}
