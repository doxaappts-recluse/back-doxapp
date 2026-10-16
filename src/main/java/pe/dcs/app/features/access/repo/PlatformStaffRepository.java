package pe.dcs.app.features.access.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import pe.dcs.app.features.access.domain.AccessStatus;
import pe.dcs.app.features.access.domain.PlatformStaff;
import pe.dcs.app.features.access.domain.StaffRole;
import pe.dcs.app.shared.vo.DocumentType;

import java.util.UUID;

public interface PlatformStaffRepository extends JpaRepository<PlatformStaff, UUID>, JpaSpecificationExecutor<PlatformStaff> {

    long countByStaffRoleAndStatus(StaffRole role, AccessStatus status);

    long count();

    boolean existsByDocTypeAndDocNumberAndIdNot(DocumentType docType, String docNumber, UUID id);

    boolean existsByDocTypeAndDocNumber(DocumentType docType, String docNumber);

    @Query("select count(s) > 0 from PlatformStaff s where lower(s.email) = lower(:email) and (:excludeId is null or s.id <> :excludeId)")
    boolean emailTaken(String email, UUID excludeId);
}
