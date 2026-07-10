package pe.dcs.app.features.user.access_user.mapper;

import pe.dcs.app.entity.Credential;
import pe.dcs.app.entity.User;
import pe.dcs.app.entity.UserAccess;
import pe.dcs.app.features.user.access_user.response.AccessSummaryResponse;
import pe.dcs.app.features.user.access_user.response.AccessUserSearchResponse;
import pe.dcs.app.features.user.shared.BaseUserMapper;
import pe.dcs.app.features.user.shared.BaseUserSearchResponse;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;

public class AccessUserMapper {

    private AccessUserMapper() {
    }

    public static AccessUserSearchResponse map(User user) {

        AccessUserSearchResponse res = new AccessUserSearchResponse();

        BaseUserSearchResponse base = BaseUserMapper.base(user);

        res.setId(base.getId());
        res.setName(base.getName());
        res.setLastname(base.getLastname());

        Credential credential = user.getCredential();

        res.setHasCredential(credential != null);
        res.setCredentialActive(credential != null && credential.canLogin());
        res.setUsername(
                credential != null
                        ? credential.getUsername()
                        : null
        );

        List<AccessSummaryResponse> accesses =

                user.getAccesses()
                        .stream()
                        .filter(UserAccess::getActive)
                        .map(access ->
                                new AccessSummaryResponse(
                                        access.getOrganization() != null
                                                ? access.getOrganization().getName()
                                                : null,
                                        access.getBranch() != null
                                                ? access.getBranch().getName()
                                                : "Todas las sedes",
                                        access.getRole() != null
                                                ? access.getRole().getValue()
                                                : null
                                )
                        )
                        .toList();

        res.setAccesses(accesses);

        return res;
    }

}