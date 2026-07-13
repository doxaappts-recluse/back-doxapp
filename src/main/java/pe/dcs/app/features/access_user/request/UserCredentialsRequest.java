package pe.dcs.app.features.access_user.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserCredentialsRequest {
    private String username;
    private String password;
}
