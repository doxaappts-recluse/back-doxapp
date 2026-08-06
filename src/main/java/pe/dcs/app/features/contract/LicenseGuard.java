package pe.dcs.app.features.contract;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Contract;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.repository.ContractBranchLicenseRepository;
import pe.dcs.app.repository.ContractRepository;
import pe.dcs.app.repository.UserAccessRepository;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.contract.ContractScope;
import pe.dcs.app.util.enums.contract.LicenseDistributionMode;

import java.util.List;

/**
 * Valida el cupo de licencias (`Contract.maxLicenses`) antes de
 * dejar ACTIVO un nuevo UserAccess (alta, reactivación de un
 * acceso adicional, o reactivación de un acceso deshabilitado).
 *
 * Si la organización/sede no tiene contrato activo, no gatea acá
 * — esa validación (contrato requerido) es responsabilidad de
 * otro punto del flujo; este guard solo limita CUÁNTOS accesos
 * activos caben dentro de un contrato que sí existe.
 */
@Service
@RequiredArgsConstructor
public class LicenseGuard {

    private final ContractRepository contractRepository;
    private final ContractBranchLicenseRepository contractBranchLicenseRepository;
    private final UserAccessRepository userAccessRepository;

    @Transactional(readOnly = true)
    public void assertLicenseAvailable(Organization organization, Branch branch) {

        if (organization == null) {
            return;
        }

        if (branch == null) {
            assertOrganizationLicense(organization);
            return;
        }

        assertBranchLicense(organization, branch);
    }

    /**
     * Acceso global a la organización (ORG_ADMIN, sin sede):
     * se descuenta del pool total del contrato de organización,
     * sin importar distributionMode (un acceso global no puede
     * "pertenecer" a la asignación de una sede puntual).
     */
    private void assertOrganizationLicense(Organization organization) {

        List<Contract> contracts =
                contractRepository.findActiveByOrganizationId(organization.getId());

        if (contracts.isEmpty()) {
            return;
        }

        Contract contract = contracts.get(0);

        long activeCount =
                userAccessRepository.countByOrganizationIdAndActive(
                        organization.getId(),
                        StatusType.ACTIVE
                );

        if (activeCount >= contract.getMaxLicenses()) {

            throw new Exceptions(
                    "error.licenciasAgotadasOrganizacion",
                    HttpStatus.CONFLICT
            );
        }
    }

    /**
     * Acceso puntual a una sede (ORG_BRANCH_ADMIN/ORG_USER):
     * se valida contra TODOS los contratos activos que aplican a
     * esa sede (propio de sede + de organización, igual que
     * ContractResolver.getActiveContractsByBranch). El tope de
     * organización (maxLicenses) siempre se respeta; si además el
     * contrato de organización distribuye por sede (ALLOCATED),
     * también se respeta el cupo específico de esa sede.
     */
    private void assertBranchLicense(Organization organization, Branch branch) {

        List<Contract> contracts =
                contractRepository.findActiveContractsForBranch(branch.getId());

        if (contracts.isEmpty()) {
            return;
        }

        long activeBranchCount =
                userAccessRepository.countByBranchIdAndActive(
                        branch.getId(),
                        StatusType.ACTIVE
                );

        for (Contract contract : contracts) {

            if (contract.getScope() == ContractScope.BRANCH) {

                if (activeBranchCount >= contract.getMaxLicenses()) {

                    throw new Exceptions(
                            "error.licenciasAgotadasSede",
                            HttpStatus.CONFLICT
                    );
                }

                continue;
            }

            // scope = ORGANIZATION

            long activeOrgCount =
                    userAccessRepository.countByOrganizationIdAndActive(
                            organization.getId(),
                            StatusType.ACTIVE
                    );

            if (activeOrgCount >= contract.getMaxLicenses()) {

                throw new Exceptions(
                        "error.licenciasAgotadasOrganizacion",
                        HttpStatus.CONFLICT
                );
            }

            if (contract.getDistributionMode() == LicenseDistributionMode.ALLOCATED) {

                int allocated =
                        contractBranchLicenseRepository.findAllocatedLicenses(
                                        contract.getId(),
                                        branch.getId()
                                )
                                .orElse(0);

                if (activeBranchCount >= allocated) {

                    throw new Exceptions(
                            "error.licenciasAgotadasSede",
                            HttpStatus.CONFLICT
                    );
                }
            }
        }
    }
}
