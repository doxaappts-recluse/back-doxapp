package pe.dcs.app.features.hr.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.hr.HrApprovalStatus;
import pe.dcs.app.util.enums.hr.HrLeaveType;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class LeaveRequestFilterRequest {

    private UUID staffId;
    private HrLeaveType type;
    private HrApprovalStatus status;
    private LocalDate fromDate;
    private LocalDate toDate;

    /**
     * Solo relevante para org admin (acotar a una sede puntual
     * dentro de su organización, ver Reportes Avanzados); branch
     * admin/org user delegado ya queda fijado a su sede actual por
     * LeaveRequestSpecification.
     */
    private UUID branchId;
}
