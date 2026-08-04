package pe.dcs.app.features.hr.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.hr.HrContractType;

import java.util.UUID;

@Getter
@Setter
public class StaffMemberFilterRequest {

    /** Busca por nombre/apellido/DNI de la persona vinculada. */
    private String search;

    private String position;
    private HrContractType contractType;
    private UUID branchId;
    private StatusType status;
}
