package pe.dcs.app.features.marriage.service;

import pe.dcs.app.features.marriage.request.MarriageFormRequest;
import pe.dcs.app.features.marriage.request.MarriageSearchRequest;
import pe.dcs.app.features.marriage.response.MarriageDetailResponse;
import pe.dcs.app.features.marriage.response.MarriageSearchRowResponse;
import pe.dcs.app.features.marriage.response.MarriageSpouseSearchResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

public interface MarriageService {

    PageResponse<MarriageSearchRowResponse> search(MarriageSearchRequest request);

    MarriageDetailResponse getById(UUID id);

    MarriageSpouseSearchResponse findSpouseByDni(String dni);

    void create(MarriageFormRequest request);

    void update(UUID id, MarriageFormRequest request);
}
