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
import java.util.UUID;

@Component
@RequiredArgsConstructor
class CustomPermissionEvaluator implements PermissionEvaluator {

    private final AuthContext authContext;
    private final ContractRepository contractRepository;
    private final ContractModuleRepository contractModuleRepository;
    private final ContractModulePermissionRepository contractModulePermissionRepository;

    @Override
    public boolean hasPermission(
            Authentication auth,
            Object targetDomainObject,
            Object permission
    ) {
        return false;
    }

    @Override
    public boolean hasPermission(
            Authentication auth,
            Serializable targetId,
            String moduleCode,
            Object permission
    ) {

        CredentialDetailsImpl user =
                (CredentialDetailsImpl) auth.getPrincipal();

        // =====================================================
        // 1. SYSTEM → FULL ACCESS
        // =====================================================
        if (isSystem(user)) {
            return true;
        }

        // =====================================================
        // 2. ORGANIZATION CONTEXT
        // =====================================================
        UUID orgId = authContext.getOrganizationId();

        if (orgId == null) {
            return false;
        }

        Contract contract =
                contractRepository
                        .findActiveByOrganizationId(orgId)
                        .orElse(null);

        if (contract == null) {
            return false;
        }

        // =====================================================
        // 3. MODULE ENABLED
        // =====================================================
        ContractModule module =
                contractModuleRepository
                        .findByContractIdAndModuleCode(
                                contract.getId(),
                                moduleCode
                        )
                        .orElse(null);

        if (module == null) {
            return false;
        }

        // =====================================================
        // 4. PERMISSION CHECK
        // =====================================================
        return contractModulePermissionRepository
                .existsByContractModuleIdAndPermissionCode(
                        module.getId(),
                        permission.toString()
                );
    }

    // =========================================================
    // SYSTEM CHECK
    // =========================================================
    private boolean isSystem(CredentialDetailsImpl user) {

        return user.getAuthorities()
                .stream()
                .anyMatch(a ->
                        a.getAuthority().equals("SYSTEM")
                );
    }
}