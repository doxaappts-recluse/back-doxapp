package pe.dcs.app.features.hr.request;

import lombok.Getter;
import lombok.Setter;

/** Usado tanto para aprobar como para rechazar — observations es obligatorio solo al rechazar (ver HrServiceImpl.rejectLeaveRequest). */
@Getter
@Setter
public class LeaveRequestDecisionRequest {

    private String observations;
}
