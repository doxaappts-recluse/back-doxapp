package pe.dcs.app.features.event.response.reports;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class BranchReportResponse {

    private UUID branchId;

    private String branchName;

    private Long total;

    private BigDecimal amount;
}
