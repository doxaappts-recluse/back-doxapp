package pe.dcs.app.features.hr.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.hr.HrContractType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class StaffMemberResponse extends AuditableResponse {

    private UUID id;

    private UUID personId;
    private String personName;
    private String personLastname;
    private String personDni;

    private UUID branchId;
    private String branchName;

    private String position;
    private HrContractType contractType;
    private BigDecimal baseSalary;

    private LocalDate hireDate;
    private LocalDate terminationDate;

    private String notes;

    private StatusType status;

    private long payrollRecordCount;
    private long pendingLeaveRequestCount;

    private boolean canManage;
}
