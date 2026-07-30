package pe.dcs.app.features.ministerial_service.service;

import pe.dcs.app.features.ministerial_service.request.MinisterialServiceSearchRequest;
import pe.dcs.app.features.ministerial_service.response.MinisterialServiceResponse;
import pe.dcs.app.util.pagination.PageResponse;

public interface MinisterialServiceService {

    PageResponse<MinisterialServiceResponse> search(MinisterialServiceSearchRequest request);

}
