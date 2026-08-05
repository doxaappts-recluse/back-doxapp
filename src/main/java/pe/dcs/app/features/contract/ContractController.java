package pe.dcs.app.features.contract;

import jakarta.validation.Valid;
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
                "success.contratosObtenidosCorrectamente",
                contractService.search(request)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<ContractResponse> getById(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                200,
                "success.contratoEncontrado",
                contractService.getById(id)
        );
    }

    /**
     * A diferencia del resto de este controller (search/getById/
     * create/update/activate/...), NO requiere ser SYSTEM — es
     * información de solo lectura sobre los módulos contratados del
     * propio contexto (org/sede) del caller autenticado, pensada
     * para gatear features de UI (org admin, branch admin u org
     * user delegado). Ver ContractServiceImpl.getActiveModuleCodesForCurrentContext().
     */
    @GetMapping("/active-module-codes")
    public ApiResponse<List<String>> getActiveModuleCodes() {

        return new ApiResponse<>(
                200,
                "success.modulosContratadosObtenidosCorrectamente",
                contractService.getActiveModuleCodesForCurrentContext()
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
                "success.catalogoModulosObtenidoCorrectamente",
                contractModuleService.getCatalog(contractId)
        );
    }

    @PostMapping("/create")
    public ApiResponse<ContractResponse> create(
            @Valid @RequestBody ContractCreateRequest request
    ) {

        return new ApiResponse<>(
                200,
                "success.contratoCreadoCorrectamente",
                contractService.create(request)
        );
    }

    @PutMapping("/update/{id}")
    public ApiResponse<ContractResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ContractUpdateRequest request
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
                        ? "success.contratoActualizadoCorrectamente"
                        : "success.seGeneroNuevoContratoCambios";

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
                "success.historialOrganizacionObtenidoCorrectamente",
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
                "success.historialSedeObtenidoCorrectamente",
                contractService.historyByBranch(branchId, request)
        );
    }

    @PatchMapping("/{id}/activate")
    public ApiResponse<Void> activate(@PathVariable UUID id) {

        contractService.activate(id);

        return new ApiResponse<>(
                200,
                "success.contratoActivadoCorrectamente",
                null
        );
    }

    @PatchMapping("/{id}/reactivate")
    public ApiResponse<Void> reactivate(@PathVariable UUID id) {

        contractService.reactivate(id);

        return new ApiResponse<>(
                200,
                "success.contratoReactivadoCorrectamente",
                null
        );
    }

    @PatchMapping("/{id}/suspend")
    public ApiResponse<Void> suspend(@PathVariable UUID id) {

        contractService.suspend(id);

        return new ApiResponse<>(
                200,
                "success.contratoSuspendidoCorrectamente",
                null
        );
    }

    @PatchMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable UUID id) {

        contractService.cancel(id);

        return new ApiResponse<>(
                200,
                "success.contratoCanceladoCorrectamente",
                null
        );
    }

}
