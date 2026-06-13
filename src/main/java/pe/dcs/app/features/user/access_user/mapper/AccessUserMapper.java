package pe.dcs.app.features.user.access_user.mapper;

import pe.dcs.app.entity.Credential;
import pe.dcs.app.entity.User;
import pe.dcs.app.features.user.access_user.response.AccessUserSearchResponse;
import pe.dcs.app.features.user.shared.BaseUserMapper;
import pe.dcs.app.features.user.shared.BaseUserSearchResponse;
import pe.dcs.app.util.enums.StatusType;

public class AccessUserMapper {

    public static AccessUserSearchResponse map(User user) {

        AccessUserSearchResponse res = new AccessUserSearchResponse();

        BaseUserSearchResponse base = BaseUserMapper.base(user);

        res.setId(base.getId());
        res.setName(base.getName());
        res.setLastname(base.getLastname());

        Credential credential = user.getCredential();

        res.setHasCredential(credential != null);
        res.setCredentialActive(
                credential != null && credential.getStatus() == StatusType.ACTIVE
        );

        res.setUsername(
                credential != null ? credential.getUsername() : null
        );

        res.setRole(
                user.getRole() != null ? user.getRole().getValue() : null
        );

        return res;
    }
}