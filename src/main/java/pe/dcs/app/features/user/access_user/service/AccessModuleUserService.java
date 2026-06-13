package pe.dcs.app.features.user.access_user.service;

import pe.dcs.app.features.user.access_user.request.UserAccessUpsertRequest;
import pe.dcs.app.features.user.access_user.response.UserAccessConfigResponse;

import java.util.UUID;

public interface AccessModuleUserService {

    UserAccessConfigResponse getConfig(UUID userId);

    void upsertAccess(UUID userId, UserAccessUpsertRequest request);
}
