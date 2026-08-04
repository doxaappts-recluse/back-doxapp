package pe.dcs.app.features.finance.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class FinancialBudgetResponse extends AuditableResponse {

    private UUID id;

    private UUID organizationId;

    private UUID branchId;
    private String branchName;

    private UUID fundId;
    private String fundName;

    private String category;

    private Integer periodYear;
    private Integer periodMonth;

    private String name;
    private String description;
    private BigDecimal amount;

    private String status;
}
