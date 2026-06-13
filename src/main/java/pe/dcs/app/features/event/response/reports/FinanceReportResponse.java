package pe.dcs.app.features.event.response.reports;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class FinanceReportResponse {
    private LocalDate date;
    private BigDecimal income;
    private BigDecimal expense;
}