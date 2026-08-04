package pe.dcs.app.features.hr.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class PayrollRecordFilterRequest {

    private UUID staffId;
    private Integer periodMonth;
    private Integer periodYear;
    private LocalDate fromDate;
    private LocalDate toDate;
}
