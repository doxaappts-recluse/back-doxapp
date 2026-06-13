package pe.dcs.app.features.user.membership_user.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.membership.MembershipExitReason;
import pe.dcs.app.util.enums.membership.MembershipStatus;

import java.time.LocalDate;

@Getter
@Setter
public class MembershipRequest {

    @NotNull
    private LocalDate startDate;

    private LocalDate endDate;

    @NotNull
    private MembershipStatus status;

    @NotNull
    private MembershipExitReason exitReason;

    private String reason;

    private String notes;

}
