package pe.dcs.app.features.contract.request;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Reparto de licencias hacia una sede específica.
 * Solo aplica cuando el contrato es de organización
 * y distributionMode = ALLOCATED.
 */
@Getter
@Setter
public class ContractBranchLicenseRequest {

    private UUID branchId;

    private Integer allocatedLicenses;
}
