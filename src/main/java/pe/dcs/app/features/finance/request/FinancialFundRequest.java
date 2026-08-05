package pe.dcs.app.features.finance.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FinancialFundRequest {

    @NotBlank(message = "{error.codigoFondoObligatorio}")
    private String code;

    @NotBlank(message = "{error.nombreEsFondoObligatorio}")
    private String nameEs;

    @NotBlank(message = "{error.nombreEnFondoObligatorio}")
    private String nameEn;

    private String description;
}
