package pe.dcs.app.features.access.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.access.repo.UserAccessRepository;
import pe.dcs.app.features.contract.domain.Contract;
import pe.dcs.app.features.contract.domain.ContractBranchLicenseRepository;
import pe.dcs.app.features.contract.domain.ContractRepository;
import pe.dcs.app.features.contract.domain.ContractScope;
import pe.dcs.app.features.contract.domain.ContractStatus;
import pe.dcs.app.features.contract.domain.LicenseDistributionMode;
import pe.dcs.app.util.Exceptions;

import java.util.List;
import java.util.UUID;

/**
 * [V11] Licencias: antes de crear o reactivar un acceso administrativo (ORG_ADMIN, ORG_BRANCH_ADMIN, ORG_USER; MEMBER
 * no consume) se comprueba que quede cupo en el contrato ACTIVE. Cuentan los accesos ACTIVE y las invitaciones
 * pendientes (un cupo queda reservado desde la invitación, así aceptar nunca supera el máximo).
 * Sin contrato ACTIVE no hay tope que aplicar (p. ej. el administrador se crea antes de contratar).
 * Llamar con la fila de la organización bloqueada para que dos altas simultáneas no superen el máximo.
 */
@Component
@RequiredArgsConstructor
public class LicenseGuard {

    private final ContractRepository contracts;
    private final ContractBranchLicenseRepository allocations;
    private final UserAccessRepository accesses;

    public void assertAvailable(UUID orgId, UUID branchId) {
        List<Contract> active = contracts.findByOrganizationIdAndStatus(orgId, ContractStatus.ACTIVE);
        for (Contract c : active) {
            if (c.getScope() == ContractScope.ORGANIZATION) {
                if (accesses.reservedInOrganization(orgId) >= c.getMaxLicenses()) {
                    throw new Exceptions("error.access.licensesOrg", HttpStatus.UNPROCESSABLE_ENTITY);
                }
                if (branchId != null && c.getDistributionMode() == LicenseDistributionMode.ALLOCATED) {
                    int allocated = allocations.findByContractId(c.getId()).stream()
                            .filter(a -> branchId.equals(a.getBranchId())).mapToInt(a -> a.getAllocatedLicenses()).findFirst().orElse(0);
                    if (accesses.reservedInBranch(orgId, branchId) >= allocated) {
                        throw new Exceptions("error.access.licensesBranch", HttpStatus.UNPROCESSABLE_ENTITY);
                    }
                }
            } else if (branchId != null && branchId.equals(c.getBranchId())
                    && accesses.reservedInBranch(orgId, branchId) >= c.getMaxLicenses()) {
                throw new Exceptions("error.access.licensesBranch", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
    }
}
