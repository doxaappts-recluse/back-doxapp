package pe.dcs.app.features.report.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PastoralFollowUpCard {

    /** Personas con membresía vigente en estado INACTIVE. */
    private long inactiveMembers;
}
