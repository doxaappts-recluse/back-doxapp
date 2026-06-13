package pe.dcs.app.features.user.baptism_user.service;

import pe.dcs.app.features.user.baptism_user.request.BaptismRequest;
import pe.dcs.app.features.user.baptism_user.request.BaptismSearchRequest;
import pe.dcs.app.features.user.baptism_user.response.BaptismDetailResponse;
import pe.dcs.app.features.user.baptism_user.response.BaptismSearchResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

public interface BaptismService {

    BaptismDetailResponse create(
            BaptismRequest request
    );

    BaptismDetailResponse update(
            UUID id,
            BaptismRequest request
    );

    BaptismDetailResponse getById(
            UUID id
    );

    PageResponse<BaptismSearchResponse> search(
            BaptismSearchRequest request
    );

}