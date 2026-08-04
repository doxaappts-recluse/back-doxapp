package pe.dcs.app.features.marriage.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class MarriageSearchRowResponse extends AuditableResponse {

    private UUID id;

    private String spouse1Name;
    private String spouse2Name;

    private LocalDate marriageDate;

    private String churchName;

    private UUID branchId;
    private String branchName;

    private boolean verified;

    private BigDecimal feeAmount;
}
