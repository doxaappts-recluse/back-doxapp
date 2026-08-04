package pe.dcs.app.features.baptism.service;

import pe.dcs.app.features.baptism.request.BaptismFormRequest;
import pe.dcs.app.features.baptism.request.BaptismSearchRequest;
import pe.dcs.app.features.baptism.response.BaptismContextResponse;
import pe.dcs.app.features.baptism.response.BaptismSearchRowResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

public interface BaptismService {

    PageResponse<BaptismSearchRowResponse> search(BaptismSearchRequest request);

    BaptismContextResponse getCurrent(UUID userId);

    void create(UUID userId, BaptismFormRequest request);

    void update(UUID userId, UUID baptismId, BaptismFormRequest request);
}
