package pe.dcs.app.features.hr.service;

import pe.dcs.app.features.hr.request.LeaveRequestDecisionRequest;
import pe.dcs.app.features.hr.request.LeaveRequestFormRequest;
import pe.dcs.app.features.hr.request.LeaveRequestSearchRequest;
import pe.dcs.app.features.hr.request.PayrollRecordFormRequest;
import pe.dcs.app.features.hr.request.PayrollRecordSearchRequest;
import pe.dcs.app.features.hr.request.StaffMemberFormRequest;
import pe.dcs.app.features.hr.request.StaffMemberSearchRequest;
import pe.dcs.app.features.hr.response.HrPersonSearchResponse;
import pe.dcs.app.features.hr.response.LeaveRequestResponse;
import pe.dcs.app.features.hr.response.PayrollRecordResponse;
import pe.dcs.app.features.hr.response.StaffMemberResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

public interface HrService {

    /** Buscar persona por DNI para crear una ficha de empleado — mismo patrón que Inventory/SpaceReservation. */
    HrPersonSearchResponse findPersonByDni(String dni);

    // Ficha de empleado
    PageResponse<StaffMemberResponse> searchStaff(StaffMemberSearchRequest request);

    StaffMemberResponse getStaffById(UUID id);

    UUID createStaff(StaffMemberFormRequest request);

    void updateStaff(UUID id, StaffMemberFormRequest request);

    // Vacaciones / Permisos
    PageResponse<LeaveRequestResponse> searchLeaveRequests(LeaveRequestSearchRequest request);

    LeaveRequestResponse getLeaveRequestById(UUID id);

    UUID createLeaveRequest(LeaveRequestFormRequest request);

    void approveLeaveRequest(UUID id, LeaveRequestDecisionRequest request);

    void rejectLeaveRequest(UUID id, LeaveRequestDecisionRequest request);

    // Planilla
    PageResponse<PayrollRecordResponse> searchPayrollRecords(PayrollRecordSearchRequest request);

    PayrollRecordResponse getPayrollRecordById(UUID id);

    UUID createPayrollRecord(PayrollRecordFormRequest request);
}
