package pe.dcs.app.features.membership.response;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class MembershipUserResponse {

    private UUID id;

    private String name;

    private String lastname;
}
