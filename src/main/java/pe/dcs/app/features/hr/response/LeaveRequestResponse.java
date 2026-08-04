package pe.dcs.app.features.hr.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.hr.HrApprovalStatus;
import pe.dcs.app.util.enums.hr.HrLeaveType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class LeaveRequestResponse extends AuditableResponse {

    private UUID id;

    private UUID staffId;
    private String staffName;

    private UUID branchId;
    private String branchName;

    private HrLeaveType type;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;

    private HrApprovalStatus status;

    private UUID approvedByUserId;
    private String approvedByUserName;
    private Instant approvedAt;

    private String observations;

    private boolean canManage;
}
