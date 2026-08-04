package pe.dcs.app.features.event.response.reports;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PaymentStatusReportResponse {

    private String status;

    private Long total;

    private BigDecimal amount;
}
