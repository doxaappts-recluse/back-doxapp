package pe.dcs.app.features.contract.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.contract.ContractRenewalType;
import pe.dcs.app.util.enums.contract.ContractScope;
import pe.dcs.app.util.enums.contract.ContractStatus;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class ContractFilterRequest {

    private UUID organizationId;

    private UUID branchId;

    private ContractScope scope;

    private String planName;

    private ContractStatus status;

    private ContractRenewalType renewalType;

    private LocalDate startDate;

    private LocalDate endDate;
}
