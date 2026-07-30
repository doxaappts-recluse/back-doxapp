package pe.dcs.app.features.contract;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.contract.request.ContractCreateRequest;
import pe.dcs.app.features.contract.request.ContractListRequest;
import pe.dcs.app.features.contract.request.ContractUpdateRequest;
import pe.dcs.app.features.contract.response.ContractModuleConfigResponse;
import pe.dcs.app.features.contract.response.ContractResponse;
import pe.dcs.app.features.contract.response.ContractResponseSearch;
import pe.dcs.app.features.contract.service.ContractModuleService;
import pe.dcs.app.features.contract.service.ContractService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/**
 * Gestión de contratos: solo SYSTEM_ADMIN / SYSTEM_SUPPORT
 * (se valida en el service). Un contrato es lo que habilita
 * módulos/permisos para una organización o una sede específica.
 */
@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;
    private final ContractModuleService contractModuleService;

    @PostMapping("/search")
    public ApiResponse<PageResponse<ContractResponseSearch>> search(
            @RequestBody ContractListRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Contratos obtenidos correctamente",
                contractService.search(request)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<ContractResponse> getById(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                200,
                "Contrato encontrado",
                contractService.getById(id)
        );
    }

    /**
     * Catálogo de módulos hoja + permisos para pintar el
     * formulario. Sin contractId (creación) todo llega en
     * false; con contractId (edición) llega lo ya asignado.
     */
    @GetMapping("/modules/catalog")
    public ApiResponse<List<ContractModuleConfigResponse>> getModuleCatalog(
            @RequestParam(required = false) UUID contractId
    ) {

        return new ApiResponse<>(
                200,
                "Catálogo de módulos obtenido correctamente",
                contractModuleService.getCatalog(contractId)
        );
    }

    @PostMapping("/create")
    public ApiResponse<ContractResponse> create(
            @RequestBody ContractCreateRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Contrato creado correctamente",
                contractService.create(request)
        );
    }

    @PutMapping("/update/{id}")
    public ApiResponse<ContractResponse> update(
            @PathVariable UUID id,
            @RequestBody ContractUpdateRequest request
    ) {

        ContractResponse response = contractService.update(id, request);

        /*
         * Si el id de la respuesta no es el mismo que se editó, fue
         * un cambio comercial: ContractServiceImpl cerró ese
         * contrato (REPLACED) y creó uno nuevo. Avisamos explícito
         * para que no parezca que se perdió el contrato anterior.
         */
        String message =
                response.getId().equals(id)
                        ? "Contrato actualizado correctamente"
                        : "Se generó un nuevo contrato con estos cambios; el anterior quedó conservado en el historial.";

        return new ApiResponse<>(
                200,
                message,
                response
        );
    }

    @PostMapping("/history/organization/{organizationId}")
    public ApiResponse<PageResponse<ContractResponseSearch>> historyByOrganization(
            @PathVariable UUID organizationId,
            @RequestBody ContractListRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Historial de la organización obtenido correctamente",
                contractService.historyByOrganization(organizationId, request)
        );
    }

    @PostMapping("/history/branch/{branchId}")
    public ApiResponse<PageResponse<ContractResponseSearch>> historyByBranch(
            @PathVariable UUID branchId,
            @RequestBody ContractListRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Historial de la sede obtenido correctamente",
                contractService.historyByBranch(branchId, request)
        );
    }

    @PatchMapping("/{id}/activate")
    public ApiResponse<Void> activate(@PathVariable UUID id) {

        contractService.activate(id);

        return new ApiResponse<>(
                200,
                "Contrato activado correctamente",
                null
        );
    }

    @PatchMapping("/{id}/reactivate")
    public ApiResponse<Void> reactivate(@PathVariable UUID id) {

        contractService.reactivate(id);

        return new ApiResponse<>(
                200,
                "Contrato reactivado correctamente",
                null
        );
    }

    @PatchMapping("/{id}/suspend")
    public ApiResponse<Void> suspend(@PathVariable UUID id) {

        contractService.suspend(id);

        return new ApiResponse<>(
                200,
                "Contrato suspendido correctamente",
                null
        );
    }

    @PatchMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable UUID id) {

        contractService.cancel(id);

        return new ApiResponse<>(
                200,
                "Contrato cancelado correctamente",
                null
        );
    }

}
