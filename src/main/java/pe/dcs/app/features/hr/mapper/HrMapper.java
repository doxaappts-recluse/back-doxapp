package pe.dcs.app.features.hr.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.LeaveRequest;
import pe.dcs.app.entity.PayrollRecord;
import pe.dcs.app.entity.StaffMember;
import pe.dcs.app.features.hr.response.LeaveRequestResponse;
import pe.dcs.app.features.hr.response.PayrollRecordResponse;
import pe.dcs.app.features.hr.response.StaffMemberResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
public class HrMapper {

    public StaffMemberResponse toStaffResponse(
            StaffMember staff,
            long payrollRecordCount,
            long pendingLeaveRequestCount,
            boolean canManage,
            boolean showAudit
    ) {

        StaffMemberResponse response = new StaffMemberResponse();

        BaseMapper.mapAudit(staff, response, showAudit);

        response.setId(staff.getId());
        response.setPosition(staff.getPosition());
        response.setContractType(staff.getContractType());
        response.setBaseSalary(staff.getBaseSalary());
        response.setHireDate(staff.getHireDate());
        response.setTerminationDate(staff.getTerminationDate());
        response.setNotes(staff.getNotes());
        response.setStatus(staff.getStatus());
        response.setPayrollRecordCount(payrollRecordCount);
        response.setPendingLeaveRequestCount(pendingLeaveRequestCount);
        response.setCanManage(canManage);

        if (staff.getPerson() != null) {
            response.setPersonId(staff.getPerson().getId());
            response.setPersonName(staff.getPerson().getName());
            response.setPersonLastname(staff.getPerson().getLastname());
            response.setPersonDni(staff.getPerson().getDni());
        }

        if (staff.getBranch() != null) {
            response.setBranchId(staff.getBranch().getId());
            response.setBranchName(staff.getBranch().getName());
        }

        return response;
    }

    public LeaveRequestResponse toLeaveRequestResponse(
            LeaveRequest leaveRequest,
            boolean canManage,
            boolean showAudit
    ) {

        LeaveRequestResponse response = new LeaveRequestResponse();

        BaseMapper.mapAudit(leaveRequest, response, showAudit);

        response.setId(leaveRequest.getId());
        response.setType(leaveRequest.getType());
        response.setStartDate(leaveRequest.getStartDate());
        response.setEndDate(leaveRequest.getEndDate());
        response.setReason(leaveRequest.getReason());
        response.setStatus(leaveRequest.getStatus());
        response.setApprovedAt(leaveRequest.getApprovedAt());
        response.setObservations(leaveRequest.getObservations());
        response.setCanManage(canManage);

        if (leaveRequest.getApprovedByUser() != null) {
            response.setApprovedByUserId(leaveRequest.getApprovedByUser().getId());
            response.setApprovedByUserName(
                    leaveRequest.getApprovedByUser().getName() + " " + leaveRequest.getApprovedByUser().getLastname()
            );
        }

        StaffMember staff = leaveRequest.getStaff();

        if (staff != null) {

            response.setStaffId(staff.getId());

            if (staff.getPerson() != null) {
                response.setStaffName(staff.getPerson().getName() + " " + staff.getPerson().getLastname());
            }

            if (staff.getBranch() != null) {
                response.setBranchId(staff.getBranch().getId());
                response.setBranchName(staff.getBranch().getName());
            }
        }

        return response;
    }

    public PayrollRecordResponse toPayrollRecordResponse(
            PayrollRecord payroll,
            boolean canManage,
            boolean showAudit
    ) {

        PayrollRecordResponse response = new PayrollRecordResponse();

        BaseMapper.mapAudit(payroll, response, showAudit);

        response.setId(payroll.getId());
        response.setPeriodMonth(payroll.getPeriodMonth());
        response.setPeriodYear(payroll.getPeriodYear());
        response.setBaseSalary(payroll.getBaseSalary());
        response.setBonuses(payroll.getBonuses());
        response.setDeductions(payroll.getDeductions());
        response.setNetAmount(payroll.getNetAmount());
        response.setPaymentDate(payroll.getPaymentDate());
        response.setPaymentMethod(payroll.getPaymentMethod());
        response.setNotes(payroll.getNotes());
        response.setCanManage(canManage);

        if (payroll.getFinancialMovement() != null) {
            response.setFinancialMovementId(payroll.getFinancialMovement().getId());
        }

        StaffMember staff = payroll.getStaff();

        if (staff != null) {

            response.setStaffId(staff.getId());

            if (staff.getPerson() != null) {
                response.setStaffName(staff.getPerson().getName() + " " + staff.getPerson().getLastname());
            }

            if (staff.getBranch() != null) {
                response.setBranchId(staff.getBranch().getId());
                response.setBranchName(staff.getBranch().getName());
            }
        }

        return response;
    }
}
