package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pe.dcs.app.entity.PayrollRecord;

import java.util.UUID;

public interface PayrollRecordRepository extends JpaRepository<PayrollRecord, UUID>, JpaSpecificationExecutor<PayrollRecord> {

    long countByStaffId(UUID staffId);
}
