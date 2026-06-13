package pe.dcs.app.features.contract.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.contract.ContractRenewalType;
import pe.dcs.app.util.enums.contract.ContractStatus;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class ContractResponseSearch {

    private UUID id;

    private UUID organizationId;
    private String organizationName;

    private String planName;

    private Double price;

    private String currency;

    private LocalDate startDate;
    private LocalDate endDate;

    private Integer numberUsers;

    private ContractStatus status;

    private ContractRenewalType renewalType;
}