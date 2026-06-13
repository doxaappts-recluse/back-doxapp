package pe.dcs.app.features.user.membership_user.service;

import pe.dcs.app.features.user.membership_user.request.MembershipHistoryRequest;
import pe.dcs.app.features.user.membership_user.request.MembershipRequest;
import pe.dcs.app.features.user.membership_user.request.MembershipUserSearchRequest;
import pe.dcs.app.features.user.membership_user.response.MembershipContextResponse;
import pe.dcs.app.features.user.membership_user.response.MembershipResponse;
import pe.dcs.app.features.user.membership_user.response.MembershipUserSearchResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

public interface MembershipUserSearchService {

    PageResponse<MembershipUserSearchResponse> search(
            MembershipUserSearchRequest request
    );

    MembershipResponse create(
            UUID userId,
            MembershipRequest request
    );

    MembershipResponse update(
            UUID userId,
            UUID membershipId,
            MembershipRequest request
    );

    MembershipContextResponse getCurrentMembership(
            UUID userId
    );

    PageResponse<MembershipResponse> getHistory(
            UUID userId,
            MembershipHistoryRequest request
    );

}