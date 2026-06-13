package pe.dcs.app.features.user.access_user.service;

import pe.dcs.app.features.user.access_user.request.AccessUserSearchRequest;
import pe.dcs.app.features.user.access_user.request.UserCredentialsRequest;
import pe.dcs.app.features.user.access_user.response.AccessUserSearchResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

public interface AccessUserSearchService {

    PageResponse<AccessUserSearchResponse> search(AccessUserSearchRequest request);

    void enable(UUID id);

    void disable(UUID id);

    void changePassword(UUID id, UserCredentialsRequest request);

}
