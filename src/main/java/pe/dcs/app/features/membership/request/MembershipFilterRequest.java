package pe.dcs.app.features.membership.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class MembershipFilterRequest {

    private String name;

    private String lastname;

    /**
     * true = solo personas con membresía vigente (current=true).
     * false = solo personas sin membresía vigente.
     * null = sin filtrar.
     */
    private Boolean hasMembership;

    private StatusType membershipStatus;

    /** Rango sobre Membership.startDate — solo usado por Reportes Avanzados. */
    private LocalDate startDate;

    private LocalDate endDate;

    /**
     * Solo relevante para org admin/SYSTEM; para branch admin/org
     * user delegado el scope ya lo fija MembershipSpecification con
     * la sede actual.
     */
    private UUID branchId;
}
