package pe.dcs.app.features.contract.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
public class ContractBranchLicenseResponse {

    private UUID branchId;

    private String branchName;

    private Integer allocatedLicenses;
}
