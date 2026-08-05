package pe.dcs.app.features.membership.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.membership.MembershipExitReason;
import pe.dcs.app.util.enums.membership.MembershipReason;
import pe.dcs.app.util.enums.StatusType;

import java.time.LocalDate;

@Getter
@Setter
public class MembershipFormRequest {

    @NotNull(message = "{error.fechaInicioMembresiaObligatoria}")
    private LocalDate startDate;

    private StatusType status;

    @NotNull(message = "{error.condicionMembresiaObligatoria}")
    private MembershipReason reason;

    private MembershipExitReason exitReason;

    private String notes;
}
