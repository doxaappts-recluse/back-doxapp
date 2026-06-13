package pe.dcs.app.features.user.system_user.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class UserSystemFilter {
    private String name;
    private String lastname;
    private String username;
    private UUID roleId;
    private StatusType status;
}
