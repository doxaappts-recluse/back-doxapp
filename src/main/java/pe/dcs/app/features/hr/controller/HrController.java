package pe.dcs.app.features.hr.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
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
import pe.dcs.app.features.hr.service.HrService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/**
 * RRHH: ficha de empleado por sede (no delegable) + vacaciones/
 * permisos y planilla (delegables). SYSTEM no tiene acceso — ver
 * HrAccessGuard.
 */
@RestController
@RequestMapping("/api/v1/hr")
@RequiredArgsConstructor
public class HrController {

    private final HrService service;

    @GetMapping("/find-by-dni")
    public ApiResponse<HrPersonSearchResponse> findPersonByDni(@RequestParam String dni) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Persona encontrada correctamente",
                service.findPersonByDni(dni)
        );
    }

    // =====================================================
    // FICHA DE EMPLEADO
    // =====================================================

    @PostMapping("/staff/search")
    public ApiResponse<PageResponse<StaffMemberResponse>> searchStaff(@RequestBody StaffMemberSearchRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Fichas de empleado obtenidas correctamente",
                service.searchStaff(request)
        );
    }

    @GetMapping("/staff/{id}")
    public ApiResponse<StaffMemberResponse> getStaffById(@PathVariable UUID id) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Ficha de empleado obtenida correctamente",
                service.getStaffById(id)
        );
    }

    @PostMapping("/staff/create")
    public ApiResponse<UUID> createStaff(@RequestBody StaffMemberFormRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Ficha de empleado registrada correctamente",
                service.createStaff(request)
        );
    }

    @PutMapping("/staff/update/{id}")
    public ApiResponse<String> updateStaff(@PathVariable UUID id, @RequestBody StaffMemberFormRequest request) {
        service.updateStaff(id, request);
        return new ApiResponse<>(HttpStatus.OK.value(), "Ficha de empleado actualizada correctamente", "OK");
    }

    // =====================================================
    // VACACIONES / PERMISOS
    // =====================================================

    @PostMapping("/leave-requests/search")
    public ApiResponse<PageResponse<LeaveRequestResponse>> searchLeaveRequests(@RequestBody LeaveRequestSearchRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Solicitudes obtenidas correctamente",
                service.searchLeaveRequests(request)
        );
    }

    @GetMapping("/leave-requests/{id}")
    public ApiResponse<LeaveRequestResponse> getLeaveRequestById(@PathVariable UUID id) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Solicitud obtenida correctamente",
                service.getLeaveRequestById(id)
        );
    }

    @PostMapping("/leave-requests/create")
    public ApiResponse<UUID> createLeaveRequest(@RequestBody LeaveRequestFormRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Solicitud registrada correctamente",
                service.createLeaveRequest(request)
        );
    }

    @PostMapping("/leave-requests/{id}/approve")
    public ApiResponse<String> approveLeaveRequest(@PathVariable UUID id, @RequestBody(required = false) LeaveRequestDecisionRequest request) {
        service.approveLeaveRequest(id, request);
        return new ApiResponse<>(HttpStatus.OK.value(), "Solicitud aprobada correctamente", "OK");
    }

    @PostMapping("/leave-requests/{id}/reject")
    public ApiResponse<String> rejectLeaveRequest(@PathVariable UUID id, @RequestBody LeaveRequestDecisionRequest request) {
        service.rejectLeaveRequest(id, request);
        return new ApiResponse<>(HttpStatus.OK.value(), "Solicitud rechazada correctamente", "OK");
    }

    // =====================================================
    // PLANILLA
    // =====================================================

    @PostMapping("/payroll/search")
    public ApiResponse<PageResponse<PayrollRecordResponse>> searchPayrollRecords(@RequestBody PayrollRecordSearchRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Pagos de planilla obtenidos correctamente",
                service.searchPayrollRecords(request)
        );
    }

    @GetMapping("/payroll/{id}")
    public ApiResponse<PayrollRecordResponse> getPayrollRecordById(@PathVariable UUID id) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Pago de planilla obtenido correctamente",
                service.getPayrollRecordById(id)
        );
    }

    @PostMapping("/payroll/create")
    public ApiResponse<UUID> createPayrollRecord(@RequestBody PayrollRecordFormRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Pago de planilla registrado correctamente",
                service.createPayrollRecord(request)
        );
    }
}
