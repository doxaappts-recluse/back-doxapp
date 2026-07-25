package pe.dcs.app.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Contract;
import pe.dcs.app.entity.ContractModule;
import pe.dcs.app.repository.ContractModulePermissionRepository;
import pe.dcs.app.repository.ContractModuleRepository;
import pe.dcs.app.repository.ContractRepository;
import pe.dcs.app.security.service.credentials.CredentialDetailsImpl;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private final AuthContext authContext;
    private final ContractRepository contractRepository;
    private final ContractModuleRepository contractModuleRepository;
    private final ContractModulePermissionRepository contractModulePermissionRepository;

    @Override
    public boolean hasPermission(
            Authentication auth,
            Object targetDomainObject,
            Object permission
    ){
        return false;
    }

    @Override
    public boolean hasPermission(
            Authentication auth,
            Serializable targetId,
            String moduleCode,
            Object permission
    ){

        if(auth == null ||
                !(auth.getPrincipal()
                        instanceof CredentialDetailsImpl)){

            return false;
        }

        CredentialDetailsImpl user =
                (CredentialDetailsImpl)
                        auth.getPrincipal();

        // =====================================================
        // SYSTEM
        // =====================================================

        if(user.isSystemAdmin() || user.isSystemSupport()){
            return true;
        }

        // =====================================================
        // CONTEXTO ACTUAL
        // =====================================================

        UUID organizationId =
                authContext
                        .getCurrentOrganizationId();

        UUID branchId =
                authContext
                        .getCurrentBranchId();

        if(organizationId == null ||
                branchId == null){

            return false;
        }

        // =====================================================
        // VALIDAR ACCESO ORGANIZACION
        // =====================================================

        if(!user.hasOrganization(
                organizationId
        )){

            return false;
        }

        // =====================================================
        // VALIDAR ACCESO SEDE
        // =====================================================

        if(!user.hasBranch(
                organizationId,
                branchId
        )){
            // ORG_ADMIN puede entrar
            // a todas las sedes de su organización

            if(!user.hasOrganizationAdminAccess(
                    organizationId
            )){
                return false;
            }
        }

        // =====================================================
        // CONTRATO ACTIVO DE LA SEDE
        // =====================================================

        List<Contract> contracts =
                contractRepository
                        .findActiveContractsForBranch(
                                branchId
                        );


        if(contracts.isEmpty()){
            return false;
        }

        // =====================================================
        // MODULO CONTRATADO
        // =====================================================

        ContractModule contractModule =
                contracts.stream()
                        .map(
                                contract ->
                                        contractModuleRepository
                                                .findByContractIdAndModuleCode(
                                                        contract.getId(),
                                                        moduleCode
                                                )
                                                .orElse(null)
                        )
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);

        if(contractModule == null){
            return false;
        }

        // =====================================================
        // PERMISO DEL MODULO
        // =====================================================

        return contractModulePermissionRepository
                .existsByContractModuleIdAndPermissionCode(
                        contractModule.getId(),
                        permission.toString()
                );

    }

}