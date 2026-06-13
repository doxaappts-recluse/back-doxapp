package pe.dcs.app.features.user.system_user.service;

import pe.dcs.app.features.user.system_user.request.UserSystemCreateRequest;
import pe.dcs.app.features.user.system_user.request.UserSystemListRequest;
import pe.dcs.app.features.user.system_user.request.UserSystemUpdateRequest;
import pe.dcs.app.features.user.system_user.response.UserSystemResponse;
import pe.dcs.app.features.user.shared.UserChangePasswordRequest;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

public interface UserSystemService {

    PageResponse<UserSystemResponse> findAllSystem(UserSystemListRequest request);
    PageResponse<UserSystemResponse> findAllOrg(UserSystemListRequest request);

    UserSystemResponse findById(UUID id);

    UserSystemResponse create(UserSystemCreateRequest request);

    UserSystemResponse update(UUID id, UserSystemUpdateRequest request);

    void enable(UUID id);

    void disable(UUID id);

    void delete(UUID id);

    void changePassword(UUID id, UserChangePasswordRequest request);

}