package pe.dcs.app.features.membership.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Respuesta de GET /membership-user/current/{userId}:
 * datos de la persona + su membresía vigente (si tiene).
 */
@Getter
@Setter
public class MembershipContextResponse {

    private MembershipUserResponse user;

    private MembershipDetailResponse currentMembership;

    /**
     * true = existe membresía pero pertenece a otra sede sin
     * visibilidad concedida (currentMembership queda null en ese
     * caso, aunque el registro exista).
     */
    private boolean restricted;
}
