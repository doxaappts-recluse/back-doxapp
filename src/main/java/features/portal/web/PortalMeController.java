package features.portal.web;

import features.portal.dto.PortalDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import features.portal.service.DirectoryService;
import features.portal.service.PortalGuard;
import features.portal.service.PortalMeService;
import features.portal.service.PrivacyService;
import features.portal.service.PushSubscriptionService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.PublicEndpoint;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M24 N4 · lo que ve el miembro. Guardado por {@link PortalGuard#requireMember} en cada método (no por
 * {@code @ModuleAccess}: ver su Javadoc para por qué ORG_ADMIN/ORG_BRANCH_ADMIN no deben entrar aquí aunque tengan
 * V/C/E sobre PORTAL para su propia administración). {@code @PublicEndpoint} silencia el interceptor de módulo:
 * el candado real es {@code PortalGuard}, no ausencia de guardia.
 */
@PublicEndpoint
@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
public class PortalMeController {

    private final AccessScopeResolver resolver;
    private final PortalGuard guard;
    private final PortalMeService me;
    private final DirectoryService directory;
    private final PrivacyService privacy;
    private final PushSubscriptionService push;
    private final AuthorizationService authz;

    @GetMapping("/me/home")
    public ApiResponse<PortalDtos.HomeResponse> home() {
        guard.requireMember(resolver.actor());
        return new ApiResponse<>(200, "ok.common.sent", me.home(resolver.actor()));
    }

    @GetMapping("/me/profile")
    public ApiResponse<PortalDtos.ProfileResponse> profile() {
        guard.requireMember(resolver.actor());
        return new ApiResponse<>(200, "ok.common.sent", me.profile(resolver.actor()));
    }

    @GetMapping("/me/requests")
    public ApiResponse<List<PortalDtos.MyRequestRow>> myRequests() {
        guard.requireMember(resolver.actor());
        return new ApiResponse<>(200, "ok.common.sent", me.myRequests(resolver.actor()));
    }

    @GetMapping("/me/family")
    public ApiResponse<PortalDtos.MyFamilyResponse> myFamily() {
        guard.requireBlockEnabled(resolver.actor(), "FAMILY");
        return new ApiResponse<>(200, "ok.common.sent", me.family(resolver.actor()));
    }

    @GetMapping("/me/membership")
    public ApiResponse<List<PortalDtos.MembershipRow>> myMembership() {
        guard.requireBlockEnabled(resolver.actor(), "MEMBERSHIP");
        return new ApiResponse<>(200, "ok.common.sent", me.membership(resolver.actor()));
    }

    @GetMapping("/me/attendance")
    public ApiResponse<List<PortalDtos.AttendanceHistoryRow>> myAttendance() {
        guard.requireBlockEnabled(resolver.actor(), "ATTENDANCE");
        return new ApiResponse<>(200, "ok.common.sent", me.attendanceHistory(resolver.actor()));
    }

    @GetMapping("/me/groups")
    public ApiResponse<List<PortalDtos.MyGroupRow>> myGroups() {
        guard.requireBlockEnabled(resolver.actor(), "GROUPS");
        return new ApiResponse<>(200, "ok.common.sent", me.myGroups(resolver.actor()));
    }

    @GetMapping("/groups/open")
    public ApiResponse<List<PortalDtos.OpenGroupRow>> openGroups() {
        guard.requireBlockEnabled(resolver.actor(), "GROUPS");
        return new ApiResponse<>(200, "ok.common.sent", me.openGroups(resolver.actor()));
    }

    @PostMapping("/groups/{id}/join-request")
    public ApiResponse<Void> requestJoinGroup(@PathVariable UUID id, @RequestBody(required = false) PortalDtos.GroupJoinRequest req) {
        guard.requireBlockEnabled(resolver.actor(), "GROUPS");
        me.requestJoinGroup(resolver.actor(), id, req == null ? null : req.message());
        return new ApiResponse<>(200, "ok.portal.groupJoinRequested", null);
    }

    @GetMapping("/me/shifts")
    public ApiResponse<List<PortalDtos.MyShiftRow>> myShifts() {
        guard.requireBlockEnabled(resolver.actor(), "SERVICE");
        return new ApiResponse<>(200, "ok.common.sent", me.myShifts(resolver.actor()));
    }

    @PostMapping("/me/shifts/{id}/confirm")
    public ApiResponse<Void> confirmMyShift(@PathVariable UUID id) {
        guard.requireBlockEnabled(resolver.actor(), "SERVICE");
        me.confirmMyShift(resolver.actor(), id);
        return new ApiResponse<>(200, "ok.common.sent", null);
    }

    @PostMapping("/me/shifts/{id}/decline")
    public ApiResponse<Void> declineMyShift(@PathVariable UUID id, @RequestBody(required = false) PortalDtos.DeclineShiftRequest req) {
        guard.requireBlockEnabled(resolver.actor(), "SERVICE");
        me.declineMyShift(resolver.actor(), id, req == null ? null : req.reason());
        return new ApiResponse<>(200, "ok.common.sent", null);
    }

    @GetMapping("/me/documents")
    public ApiResponse<List<PortalDtos.MyDocumentRow>> myDocuments() {
        guard.requireBlockEnabled(resolver.actor(), "DOCUMENTS");
        return new ApiResponse<>(200, "ok.common.sent", me.myDocuments(resolver.actor()));
    }

    /** Bytes crudos (sin {@code ApiResponse}), como el equivalente admin en {@code AdminIssuedDocumentController}; el candado real está en {@link PortalMeService#myDocumentPdf}, no aquí. */
    @GetMapping("/me/documents/{id}/pdf")
    public ResponseEntity<byte[]> myDocumentPdf(@PathVariable UUID id) {
        guard.requireBlockEnabled(resolver.actor(), "DOCUMENTS");
        var f = me.myDocumentPdf(resolver.actor(), id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(f.data());
    }

    @GetMapping("/me/directory-preference")
    public ApiResponse<PortalDtos.DirectoryPreferenceResponse> getDirectoryPreference() {
        guard.requireMember(resolver.actor());
        return new ApiResponse<>(200, "ok.common.sent", directory.getPreference(resolver.actor().ownerId()));
    }

    @PostMapping("/me/directory-preference")
    public ApiResponse<PortalDtos.DirectoryPreferenceResponse> setDirectoryPreference(@RequestBody PortalDtos.DirectoryPreferenceRequest req) {
        guard.requireMember(resolver.actor());
        return new ApiResponse<>(200, "ok.portal.directorySaved", directory.setPreference(resolver.actor().organizationId(), resolver.actor().ownerId(), req));
    }

    /** [V15] solo alcanzable si la organización contrató PORTAL_DIRECTORY (el candado de contrato lo aplica {@code authz.require}). */
    @GetMapping("/directory")
    public ApiResponse<List<PortalDtos.DirectoryEntry>> searchDirectory(@RequestParam(required = false) String q) {
        guard.requireMember(resolver.actor());
        authz.require(resolver.actor(), "PORTAL_DIRECTORY", Action.V);
        return new ApiResponse<>(200, "ok.common.sent", directory.search(resolver.actor().organizationId(), q));
    }

    @GetMapping("/me/privacy/export")
    public ApiResponse<Map<String, Object>> exportData() {
        guard.requireMember(resolver.actor());
        return new ApiResponse<>(200, "ok.portal.exportRequested", privacy.exportData(resolver.actor()));
    }

    @PostMapping("/me/privacy/erasure-request")
    public ApiResponse<PortalDtos.DataRequestResponse> erasureRequest() {
        guard.requireMember(resolver.actor());
        return new ApiResponse<>(200, "ok.portal.erasureRequested", privacy.requestErasure(resolver.actor()));
    }

    @PostMapping("/me/push-subscriptions")
    public ApiResponse<Void> subscribe(@RequestBody PortalDtos.PushSubscribeRequest req) {
        guard.requireMember(resolver.actor());
        push.subscribe(resolver.actor().ownerId(), req);
        return new ApiResponse<>(200, "ok.portal.pushEnabled", null);
    }

    @DeleteMapping("/me/push-subscriptions")
    public ApiResponse<Void> unsubscribe(@RequestParam String endpoint) {
        guard.requireMember(resolver.actor());
        push.revoke(resolver.actor().ownerId(), endpoint);
        return new ApiResponse<>(200, "ok.portal.pushDisabled", null);
    }

    @GetMapping("/me/pastoral-cases")
    public ApiResponse<List<PortalDtos.MyPastoralCaseRow>> myPastoralCases() {
        guard.requireBlockEnabled(resolver.actor(), "PASTORAL_CARE");
        return new ApiResponse<>(200, "ok.common.sent", me.myPastoralCases(resolver.actor()));
    }

    @PostMapping("/me/pastoral-cases")
    public ApiResponse<Void> requestPastoralCare(@RequestBody(required = false) PortalDtos.RequestPastoralCareRequest req) {
        guard.requireBlockEnabled(resolver.actor(), "PASTORAL_CARE");
        me.requestPastoralCare(resolver.actor(), req == null ? null : req.type(), req == null ? null : req.note());
        return new ApiResponse<>(200, "ok.portal.pastoralCareRequested", null);
    }

    @GetMapping("/me/prayer-requests")
    public ApiResponse<List<PortalDtos.MyPrayerRequestRow>> myPrayerRequests() {
        guard.requireBlockEnabled(resolver.actor(), "PASTORAL_CARE");
        return new ApiResponse<>(200, "ok.common.sent", me.myPrayerRequests(resolver.actor()));
    }

    @PostMapping("/me/prayer-requests")
    public ApiResponse<Void> submitPrayerRequest(@RequestBody PortalDtos.SubmitPrayerRequest req) {
        guard.requireBlockEnabled(resolver.actor(), "PASTORAL_CARE");
        me.submitPrayerRequest(resolver.actor(), req == null ? null : req.text(), req == null ? null : req.category(),
                req == null ? null : req.visibility(), req == null ? null : req.anonymous());
        return new ApiResponse<>(200, "ok.portal.prayerRequestSent", null);
    }

    @GetMapping("/me/enrollments")
    public ApiResponse<List<PortalDtos.MyEnrollmentRow>> myEnrollments() {
        guard.requireBlockEnabled(resolver.actor(), "TRAINING");
        return new ApiResponse<>(200, "ok.common.sent", me.myEnrollments(resolver.actor()));
    }

    @GetMapping("/classes/open")
    public ApiResponse<List<PortalDtos.OpenClassRow>> openClasses() {
        guard.requireBlockEnabled(resolver.actor(), "TRAINING");
        return new ApiResponse<>(200, "ok.common.sent", me.openClasses(resolver.actor()));
    }

    @PostMapping("/classes/{id}/enroll")
    public ApiResponse<Void> requestEnroll(@PathVariable UUID id) {
        guard.requireBlockEnabled(resolver.actor(), "TRAINING");
        me.requestEnroll(resolver.actor(), id);
        return new ApiResponse<>(200, "ok.common.sent", null);
    }

    @PostMapping("/me/enrollments/{id}/withdraw")
    public ApiResponse<Void> withdrawEnrollment(@PathVariable UUID id, @RequestBody(required = false) PortalDtos.WithdrawEnrollmentRequest req) {
        guard.requireBlockEnabled(resolver.actor(), "TRAINING");
        me.withdrawEnrollment(resolver.actor(), id, req == null ? null : req.reason());
        return new ApiResponse<>(200, "ok.common.sent", null);
    }

    @GetMapping("/events/open")
    public ApiResponse<List<PortalDtos.OpenEventRow>> openEvents() {
        guard.requireBlockEnabled(resolver.actor(), "EVENTS");
        return new ApiResponse<>(200, "ok.common.sent", me.openEvents(resolver.actor()));
    }

    @GetMapping("/me/event-registrations")
    public ApiResponse<List<PortalDtos.MyRegistrationRow>> myRegistrations() {
        guard.requireBlockEnabled(resolver.actor(), "EVENTS");
        return new ApiResponse<>(200, "ok.common.sent", me.myRegistrations(resolver.actor()));
    }

    @PostMapping("/events/{id}/register")
    public ApiResponse<Void> registerForEvent(@PathVariable UUID id, @RequestBody(required = false) PortalDtos.RegisterEventRequest req) {
        guard.requireBlockEnabled(resolver.actor(), "EVENTS");
        me.registerForEvent(resolver.actor(), id, req == null ? null : req.guests(), req == null ? null : req.answers());
        return new ApiResponse<>(200, "ok.common.sent", null);
    }

    @PostMapping("/me/event-registrations/{id}/cancel")
    public ApiResponse<Void> cancelRegistration(@PathVariable UUID id, @RequestBody(required = false) PortalDtos.CancelRegistrationRequest req) {
        guard.requireBlockEnabled(resolver.actor(), "EVENTS");
        me.cancelRegistration(resolver.actor(), id, req == null ? null : req.reason());
        return new ApiResponse<>(200, "ok.common.sent", null);
    }

    @GetMapping("/me/giving")
    public ApiResponse<List<PortalDtos.MyGivingRow>> myGiving() {
        guard.requireBlockEnabled(resolver.actor(), "GIVING");
        return new ApiResponse<>(200, "ok.common.sent", me.myGiving(resolver.actor()));
    }

    @GetMapping("/spaces/open")
    public ApiResponse<PortalDtos.OpenSpacesResponse> openSpaces() {
        guard.requireBlockEnabled(resolver.actor(), "SPACES");
        return new ApiResponse<>(200, "ok.common.sent", me.openSpaces(resolver.actor()));
    }

    @GetMapping("/me/space-reservations")
    public ApiResponse<List<PortalDtos.MySpaceReservationRow>> mySpaceReservations() {
        guard.requireBlockEnabled(resolver.actor(), "SPACES");
        return new ApiResponse<>(200, "ok.common.sent", me.mySpaceReservations(resolver.actor()));
    }

    @PostMapping("/spaces/reservations")
    public ApiResponse<Void> requestSpaceReservation(@RequestBody PortalDtos.RequestSpaceReservationRequest req) {
        guard.requireBlockEnabled(resolver.actor(), "SPACES");
        me.requestSpaceReservation(resolver.actor(), req == null ? null : req.spaceId(), req == null ? null : req.title(),
                req == null ? null : req.startAt(), req == null ? null : req.endAt(), req == null ? null : req.attendeesEst());
        return new ApiResponse<>(200, "ok.common.sent", null);
    }

    @GetMapping("/me/work")
    public ApiResponse<PortalDtos.MyWorkResponse> myWork() {
        guard.requireBlockEnabled(resolver.actor(), "MY_WORK");
        return new ApiResponse<>(200, "ok.common.sent", me.myWork(resolver.actor()));
    }
}
