package pe.dcs.app.features.contract;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.contract.request.ContractCreateRequest;
import pe.dcs.app.features.contract.request.ContractListRequest;
import pe.dcs.app.features.contract.response.ContractResponse;
import pe.dcs.app.features.contract.response.ContractResponseSearch;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.features.contract.service.ContractService;
import pe.dcs.app.util.ApiResponse;

import java.util.UUID;


@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @PostMapping("/search")
    public ApiResponse<PageResponse<ContractResponseSearch>>
    search(
            @RequestBody
                    ContractListRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Contratos obtenidos correctamente",
                contractService.search(request)
        );
    }

    @PostMapping("/history/{organizationId}")
    public ApiResponse<PageResponse<ContractResponseSearch>>
    history(
            @PathVariable UUID organizationId,
            @RequestBody ContractListRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Historial obtenido correctamente",
                contractService.history(
                        organizationId,
                        request
                )
        );
    }

    @GetMapping("/{organizationId}/base")
    public ApiResponse<ContractResponse> getBaseContract(
            @PathVariable UUID organizationId
    ) {

        return new ApiResponse<>(
                200,
                "Contrato cargado correctamente",
                contractService.getBaseContract(organizationId)
        );
    }

    @PostMapping("/process")
    public ApiResponse<Void> process(
            @RequestBody
                    ContractCreateRequest request
    ) {
        contractService.process(request);
        return new ApiResponse<>(
                200,
                "Contrato procesado correctamente",
                null
        );
    }

    @PutMapping("/{id}/suspend")
    public ApiResponse<Void> suspend(@PathVariable UUID id) {
        contractService.suspend(id);
        return new ApiResponse<>(
                200,
                "Contrato suspendido correctamente",
                null
        );
    }

    @PutMapping("/{id}/reactivate")
    public ApiResponse<Void> reactivate(@PathVariable UUID id) {
        contractService.reactivate(id);
        return new ApiResponse<>(
                200,
                "Contrato reactivado correctamente",
                null
        );
    }

    @PutMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable UUID id) {
        contractService.cancel(id);
        return new ApiResponse<>(
                200,
                "Contrato suspendido correctamente",
                null
        );
    }

    @PutMapping("/{id}/activate")
    public ApiResponse<Void> activate(@PathVariable UUID id) {
        contractService.activate(id);
        return new ApiResponse<>(
                200,
                "Contrato reactivado correctamente",
                null
        );
    }
}