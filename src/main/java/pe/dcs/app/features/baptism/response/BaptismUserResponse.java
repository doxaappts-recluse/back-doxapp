package pe.dcs.app.features.baptism.response;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class BaptismUserResponse {

    private UUID id;

    private String name;

    private String lastname;
}
