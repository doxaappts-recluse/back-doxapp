package pe.dcs.app.features.hr.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.hr.HrLeaveType;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class LeaveRequestFormRequest {

    private UUID staffId;
    private HrLeaveType type;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
}
