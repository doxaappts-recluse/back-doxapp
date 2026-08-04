package pe.dcs.app.features.pastoral_followup.service;

import pe.dcs.app.features.pastoral_followup.request.AssignLeaderRequest;
import pe.dcs.app.features.pastoral_followup.request.FollowUpContactFormRequest;
import pe.dcs.app.features.pastoral_followup.request.InactiveMemberSearchRequest;
import pe.dcs.app.features.pastoral_followup.request.PastoralFollowUpHistoryRequest;
import pe.dcs.app.features.pastoral_followup.request.PrayerRequestFormRequest;
import pe.dcs.app.features.pastoral_followup.response.FollowUpContactResponse;
import pe.dcs.app.features.pastoral_followup.response.InactiveMemberResponse;
import pe.dcs.app.features.pastoral_followup.response.PastoralFollowUpSummaryResponse;
import pe.dcs.app.features.pastoral_followup.response.PrayerRequestResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

public interface PastoralFollowUpService {

    PastoralFollowUpSummaryResponse getSummary(UUID personId);

    void assignLeader(UUID personId, AssignLeaderRequest request);

    PageResponse<FollowUpContactResponse> listContacts(
            UUID personId,
            PastoralFollowUpHistoryRequest request
    );

    void createContact(UUID personId, FollowUpContactFormRequest request);

    void updateContact(UUID personId, UUID contactId, FollowUpContactFormRequest request);

    PageResponse<PrayerRequestResponse> listPrayerRequests(
            UUID personId,
            PastoralFollowUpHistoryRequest request
    );

    void createPrayerRequest(UUID personId, PrayerRequestFormRequest request);

    void updatePrayerRequest(UUID personId, UUID prayerRequestId, PrayerRequestFormRequest request);

    /**
     * Cruza Membership (status=INACTIVE, current=true) con lo que ya
     * existe en Seguimiento Pastoral (líder asignado, último
     * contacto) — ver InactiveMemberSpecification.
     */
    PageResponse<InactiveMemberResponse> searchInactiveMembers(InactiveMemberSearchRequest request);
}
