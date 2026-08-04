package pe.dcs.app.features.pastoral_followup.request;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * leaderId=null para desasignar al líder actual (ver
 * PastoralFollowUpServiceImpl.assignLeader).
 */
@Getter
@Setter
public class AssignLeaderRequest {

    private UUID leaderId;
}
