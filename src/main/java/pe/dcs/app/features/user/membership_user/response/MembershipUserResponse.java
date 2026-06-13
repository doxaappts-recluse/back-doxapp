package pe.dcs.app.features.user.membership_user.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MembershipUserResponse {

    private UUID userId;
    private String name;
    private String lastname;

}
