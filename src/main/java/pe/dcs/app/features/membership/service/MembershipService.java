package pe.dcs.app.features.membership.service;

import pe.dcs.app.features.membership.request.MembershipFormRequest;
import pe.dcs.app.features.membership.request.MembershipHistoryRequest;
import pe.dcs.app.features.membership.request.MembershipSearchRequest;
import pe.dcs.app.features.membership.response.MembershipContextResponse;
import pe.dcs.app.features.membership.response.MembershipDetailResponse;
import pe.dcs.app.features.membership.response.MembershipSearchRowResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/**
 * Membresía de una persona: estado actual (current=true) +
 * historial (registros pasados). Crear siempre abre un nuevo
 * registro (cerrando el vigente si existía); editar solo
 * modifica el registro vigente.
 */
public interface MembershipService {

    PageResponse<MembershipSearchRowResponse> search(MembershipSearchRequest request);

    MembershipContextResponse getCurrent(UUID userId);

    void create(UUID userId, MembershipFormRequest request);

    void update(UUID userId, UUID membershipId, MembershipFormRequest request);

    PageResponse<MembershipDetailResponse> history(UUID userId, MembershipHistoryRequest request);

}
