package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pe.dcs.app.entity.VisibilityRequest;
import pe.dcs.app.util.enums.rules.VisibilityStatus;

import java.util.List;
import java.util.UUID;

public interface VisibilityRequestRepository
        extends JpaRepository<VisibilityRequest, UUID>, JpaSpecificationExecutor<VisibilityRequest> {

    List<VisibilityRequest> findByPerson_IdAndSourceBranch_IdAndModule_CodeAndStatus(
            UUID personId,
            UUID sourceBranchId,
            String moduleCode,
            VisibilityStatus status
    );
}
