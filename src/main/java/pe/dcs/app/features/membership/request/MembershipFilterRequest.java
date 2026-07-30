package pe.dcs.app.features.membership.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

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
}
