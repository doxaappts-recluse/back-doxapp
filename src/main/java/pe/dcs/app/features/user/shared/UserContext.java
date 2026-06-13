package pe.dcs.app.features.user.shared;

import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.dcs.app.util.enums.SystemRoleType;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class UserContext {

    SystemRoleType systemRole;
    UUID organizationId;
}
