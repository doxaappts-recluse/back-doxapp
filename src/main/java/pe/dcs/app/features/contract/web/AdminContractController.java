package pe.dcs.app.features.contract.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.contract.dto.AdminContractResponse;
import pe.dcs.app.features.contract.service.ContractService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

/** M03 · "Contrato" de la organización (N2, solo lectura). Disponible también en modo limitado. */
@RestController
@RequestMapping("/api/v1/admin/contract")
@RequiredArgsConstructor
public class AdminContractController {

    private static final String MODULE = "ORG_CONTRACT_VIEW";

    private final ContractService service;
    private final AccessScopeResolver resolver;

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<AdminContractResponse> get() {
        return new ApiResponse<>(200, "ok.common.sent", service.mine(resolver.actor()));
    }
}
