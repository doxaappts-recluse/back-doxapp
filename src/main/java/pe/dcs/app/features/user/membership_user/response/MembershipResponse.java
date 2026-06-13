package pe.dcs.app.features.user.membership_user.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.dcs.app.util.enums.membership.MembershipExitReason;
import pe.dcs.app.util.enums.membership.MembershipStatus;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MembershipResponse {

    private UUID id;

    private LocalDate startDate;

    private LocalDate endDate;

    private MembershipStatus status;

    private MembershipExitReason exitReason;

    private String reason;

    private Boolean current;

    private String notes;

}
