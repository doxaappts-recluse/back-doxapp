package pe.dcs.app.features.marriage.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class MarriageDetailResponse {

    private UUID id;

    private UUID spouse1PersonId;
    private String spouse1Name;
    private String spouse1Dni;
    private boolean spouse1Member;

    private UUID spouse2PersonId;
    private String spouse2Name;
    private String spouse2Dni;
    private boolean spouse2Member;

    private LocalDate marriageDate;

    private String churchName;

    private String pastorName;

    private String city;

    private boolean verified;

    private String observations;

    private BigDecimal feeAmount;

    private UUID financialMovementId;

    private UUID branchId;
    private String branchName;
}
