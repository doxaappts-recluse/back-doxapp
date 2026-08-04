package pe.dcs.app.features.report;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.baptism.response.BaptismSearchRowResponse;
import pe.dcs.app.features.event.response.event.EventResponse;
import pe.dcs.app.features.finance.response.FinancialMovementResponse;
import pe.dcs.app.features.hr.response.LeaveRequestResponse;
import pe.dcs.app.features.inventory.response.InventoryMovementResponse;
import pe.dcs.app.features.marriage.response.MarriageSearchRowResponse;
import pe.dcs.app.features.membership.response.MembershipSearchRowResponse;
import pe.dcs.app.features.pastoral_followup.response.InactiveMemberResponse;
import pe.dcs.app.features.report.request.ReportFilterRequest;
import pe.dcs.app.features.report.response.ExecutiveDashboardResponse;
import pe.dcs.app.features.smallgroup.response.SmallGroupSearchRowResponse;
import pe.dcs.app.features.space_reservation.response.SpaceReservationResponse;
import pe.dcs.app.features.visitor.response.VisitorSearchRowResponse;
import pe.dcs.app.util.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/api/v1/advanced-reports")
@RequiredArgsConstructor
public class AdvancedReportsController {

    private final AdvancedReportsService advancedReportsService;

    @GetMapping("/executive-dashboard")
    public ApiResponse<ExecutiveDashboardResponse> getExecutiveDashboard() {
        return new ApiResponse<>(
                200,
                "Executive dashboard fetched successfully",
                advancedReportsService.getExecutiveDashboard()
        );
    }

    @PostMapping("/financial-movements")
    public ApiResponse<List<FinancialMovementResponse>> getFinancialMovementsReport(
            @RequestBody ReportFilterRequest filter
    ) {
        return new ApiResponse<>(
                200,
                "Financial movements report fetched successfully",
                advancedReportsService.getFinancialMovementsReport(filter)
        );
    }

    @PostMapping("/events")
    public ApiResponse<List<EventResponse>> getEventsReport(
            @RequestBody ReportFilterRequest filter
    ) {
        return new ApiResponse<>(
                200,
                "Events report fetched successfully",
                advancedReportsService.getEventsReport(filter)
        );
    }

    @PostMapping("/membership")
    public ApiResponse<List<MembershipSearchRowResponse>> getMembershipReport(
            @RequestBody ReportFilterRequest filter
    ) {
        return new ApiResponse<>(
                200,
                "Membership report fetched successfully",
                advancedReportsService.getMembershipReport(filter)
        );
    }

    @PostMapping("/baptism")
    public ApiResponse<List<BaptismSearchRowResponse>> getBaptismReport(
            @RequestBody ReportFilterRequest filter
    ) {
        return new ApiResponse<>(
                200,
                "Baptism report fetched successfully",
                advancedReportsService.getBaptismReport(filter)
        );
    }

    @PostMapping("/marriages")
    public ApiResponse<List<MarriageSearchRowResponse>> getMarriagesReport(
            @RequestBody ReportFilterRequest filter
    ) {
        return new ApiResponse<>(
                200,
                "Marriages report fetched successfully",
                advancedReportsService.getMarriagesReport(filter)
        );
    }

    @PostMapping("/visitors")
    public ApiResponse<List<VisitorSearchRowResponse>> getVisitorsReport(
            @RequestBody ReportFilterRequest filter
    ) {
        return new ApiResponse<>(
                200,
                "Visitors report fetched successfully",
                advancedReportsService.getVisitorsReport(filter)
        );
    }

    @PostMapping("/inactive-members")
    public ApiResponse<List<InactiveMemberResponse>> getInactiveMembersReport(
            @RequestBody ReportFilterRequest filter
    ) {
        return new ApiResponse<>(
                200,
                "Inactive members report fetched successfully",
                advancedReportsService.getInactiveMembersReport(filter)
        );
    }

    @PostMapping("/small-groups")
    public ApiResponse<List<SmallGroupSearchRowResponse>> getSmallGroupsReport(
            @RequestBody ReportFilterRequest filter
    ) {
        return new ApiResponse<>(
                200,
                "Small groups report fetched successfully",
                advancedReportsService.getSmallGroupsReport(filter)
        );
    }

    @PostMapping("/space-reservations")
    public ApiResponse<List<SpaceReservationResponse>> getSpaceReservationsReport(
            @RequestBody ReportFilterRequest filter
    ) {
        return new ApiResponse<>(
                200,
                "Space reservations report fetched successfully",
                advancedReportsService.getSpaceReservationsReport(filter)
        );
    }

    @PostMapping("/inventory-movements")
    public ApiResponse<List<InventoryMovementResponse>> getInventoryMovementsReport(
            @RequestBody ReportFilterRequest filter
    ) {
        return new ApiResponse<>(
                200,
                "Inventory movements report fetched successfully",
                advancedReportsService.getInventoryMovementsReport(filter)
        );
    }

    @PostMapping("/leave-requests")
    public ApiResponse<List<LeaveRequestResponse>> getLeaveRequestsReport(
            @RequestBody ReportFilterRequest filter
    ) {
        return new ApiResponse<>(
                200,
                "Leave requests report fetched successfully",
                advancedReportsService.getLeaveRequestsReport(filter)
        );
    }
}
