package pe.dcs.app.features.finance.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;

import java.util.UUID;

@Getter
@Setter
public class FinancialFundResponse extends AuditableResponse {

    private UUID id;

    private UUID organizationId;

    private String name;
    private String description;
    private String status;
}
