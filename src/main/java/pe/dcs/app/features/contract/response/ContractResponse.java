package pe.dcs.app.features.contract.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.contract.ContractRenewalType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ContractResponse {

    private UUID id;

    private UUID organizationId;

    private String organizationName;

    private String planName;

    private Double price;

    private String currency;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer numberUsers;

    private String status;

    private ContractRenewalType renewalType;

    private UUID previousContractId;

    private List<ContractModuleResponse> modules;
}