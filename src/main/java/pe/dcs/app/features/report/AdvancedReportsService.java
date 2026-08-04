package pe.dcs.app.features.report;

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

import java.util.List;

/**
 * Dashboard Ejecutivo / Reportes Avanzados: getExecutiveDashboard()
 * es la vista agregada de tarjetas por módulo contratado. Los demás
 * métodos son las tablas de detalle filtrables por fecha/sede
 * (Finanzas, Eventos, ...) que las reemplazan en la pantalla de
 * Reportes Avanzados — ver AdvancedReportsServiceImpl.
 */
public interface AdvancedReportsService {

    ExecutiveDashboardResponse getExecutiveDashboard();

    List<FinancialMovementResponse> getFinancialMovementsReport(ReportFilterRequest filter);

    List<EventResponse> getEventsReport(ReportFilterRequest filter);

    List<MembershipSearchRowResponse> getMembershipReport(ReportFilterRequest filter);

    List<BaptismSearchRowResponse> getBaptismReport(ReportFilterRequest filter);

    List<MarriageSearchRowResponse> getMarriagesReport(ReportFilterRequest filter);

    List<VisitorSearchRowResponse> getVisitorsReport(ReportFilterRequest filter);

    List<InactiveMemberResponse> getInactiveMembersReport(ReportFilterRequest filter);

    List<SmallGroupSearchRowResponse> getSmallGroupsReport(ReportFilterRequest filter);

    List<SpaceReservationResponse> getSpaceReservationsReport(ReportFilterRequest filter);

    List<InventoryMovementResponse> getInventoryMovementsReport(ReportFilterRequest filter);

    List<LeaveRequestResponse> getLeaveRequestsReport(ReportFilterRequest filter);
}
