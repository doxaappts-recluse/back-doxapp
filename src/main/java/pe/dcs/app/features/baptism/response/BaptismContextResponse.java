package pe.dcs.app.features.baptism.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BaptismContextResponse {

    private BaptismUserResponse user;

    private BaptismDetailResponse baptism;

    /**
     * true = existe bautizo pero pertenece a otra sede sin
     * visibilidad concedida (baptism queda null en ese caso,
     * aunque el registro exista).
     */
    private boolean restricted;
}
