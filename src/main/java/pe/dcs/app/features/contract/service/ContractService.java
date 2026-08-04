package pe.dcs.app.features.contract.service;

import pe.dcs.app.features.contract.request.ContractCreateRequest;
import pe.dcs.app.features.contract.request.ContractListRequest;
import pe.dcs.app.features.contract.request.ContractUpdateRequest;
import pe.dcs.app.features.contract.response.ContractResponse;
import pe.dcs.app.features.contract.response.ContractResponseSearch;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

public interface ContractService {

    PageResponse<ContractResponseSearch> search(
            ContractListRequest request
    );

    /**
     * Codes de los módulos "assigned" en los contratos ACTIVE
     * aplicables al org/sede ACTUAL del usuario autenticado (unión
     * de contrato de organización + contrato de sede, igual que
     * SidebarService — ver ContractResolver.getActiveContractsByBranch).
     * A diferencia de search()/getById(), NO requiere ser SYSTEM: es
     * información de solo lectura sobre el propio contexto del
     * caller (org admin, branch admin u org user), pensada para
     * gatear features de UI (ej. filtrar tipos de documento
     * disponibles, o mostrar/ocultar el botón "Descargar
     * certificado") sin exponerle el módulo de gestión de contratos
     * completo, que sigue siendo exclusivo de SYSTEM.
     */
    List<String> getActiveModuleCodesForCurrentContext();

    ContractResponse getById(UUID id);

    ContractResponse create(
            ContractCreateRequest request
    );

    ContractResponse update(
            UUID id,
            ContractUpdateRequest request
    );

    PageResponse<ContractResponseSearch> historyByOrganization(
            UUID organizationId,
            ContractListRequest request
    );

    PageResponse<ContractResponseSearch> historyByBranch(
            UUID branchId,
            ContractListRequest request
    );

    void activate(UUID id);

    void reactivate(UUID id);

    void suspend(UUID id);

    void cancel(UUID id);

}
