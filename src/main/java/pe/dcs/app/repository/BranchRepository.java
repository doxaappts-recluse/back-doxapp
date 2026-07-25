package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID>, JpaSpecificationExecutor<Branch> {

    boolean existsByCodeAndOrganizationId(
            String code,
            UUID organizationId
    );

    boolean existsByCodeAndOrganizationIdAndIdNot(
            String code,
            UUID organizationId,
            UUID id
    );

    @Modifying
    @Query("""
        update Branch b
           set b.main = false
         where b.organization.id = :organizationId
    """)
    void clearMainBranch(
            @Param("organizationId") UUID organizationId
    );

    List<Branch> findByOrganizationId(
            UUID organizationId
    );

    List<Branch> findByOrganizationIdAndStatusOrderByNameAsc(
            UUID organizationId,
            StatusType status
    );
}