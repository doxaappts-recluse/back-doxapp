package pe.dcs.app.features.user.ministry_user.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MinistryRoleOptionResponse {

    private UUID roleId;

    private String roleName;

    private UUID ministryId;

    private String ministryName;
}