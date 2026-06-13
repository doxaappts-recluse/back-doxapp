package pe.dcs.app.features.user.ministry_user.service;

import pe.dcs.app.features.user.ministry_user.request.MinistryUserSearchRequest;
import pe.dcs.app.features.user.ministry_user.response.MinistryUserSearchResponse;
import pe.dcs.app.util.pagination.PageResponse;

public interface MinistryUserSearchService {

    PageResponse<MinistryUserSearchResponse> search(
            MinistryUserSearchRequest request
    );
}