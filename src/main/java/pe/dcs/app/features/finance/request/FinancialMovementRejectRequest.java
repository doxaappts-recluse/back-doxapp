package pe.dcs.app.features.finance.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FinancialMovementRejectRequest {

    @NotBlank
    private String reason;
}
