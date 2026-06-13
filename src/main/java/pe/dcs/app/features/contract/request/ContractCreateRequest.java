package pe.dcs.app.features.contract.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.contract.ContractRenewalType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ContractCreateRequest {

    private UUID organizationId;

    private String planName;

    private Double price;

    private String currency;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer numberUsers;

    private ContractRenewalType renewalType;

    private List<ContractModuleRequest> modules;
}