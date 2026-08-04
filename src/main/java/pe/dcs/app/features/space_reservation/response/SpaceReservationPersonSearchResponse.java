package pe.dcs.app.features.space_reservation.response;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class SpaceReservationPersonSearchResponse {

    private UUID personId;
    private String name;
    private String lastname;
    private String dni;
    private boolean isMember;
}
