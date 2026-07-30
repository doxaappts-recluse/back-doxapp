package pe.dcs.app.features.membership.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.membership.MembershipExitReason;
import pe.dcs.app.util.enums.membership.MembershipReason;
import pe.dcs.app.util.enums.StatusType;

import java.time.LocalDate;

@Getter
@Setter
public class MembershipFormRequest {

    private LocalDate startDate;

    private StatusType status;

    private MembershipReason reason;

    private MembershipExitReason exitReason;

    private String notes;
}
