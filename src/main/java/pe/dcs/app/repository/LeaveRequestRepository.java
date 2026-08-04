package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pe.dcs.app.entity.LeaveRequest;
import pe.dcs.app.util.enums.hr.HrApprovalStatus;

import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID>, JpaSpecificationExecutor<LeaveRequest> {

    long countByStaffIdAndStatus(UUID staffId, HrApprovalStatus status);
}
