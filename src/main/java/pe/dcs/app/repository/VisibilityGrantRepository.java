package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.dcs.app.entity.VisibilityGrant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisibilityGrantRepository extends JpaRepository<VisibilityGrant, UUID> {

    @Query("""
            select g from VisibilityGrant g
            where g.person.id = :personId
              and g.sourceBranch.id = :sourceBranchId
              and g.targetBranch.id = :targetBranchId
              and g.module.code = :moduleCode
              and g.active = true
            """)
    Optional<VisibilityGrant> findActive(
            @Param("personId") UUID personId,
            @Param("sourceBranchId") UUID sourceBranchId,
            @Param("targetBranchId") UUID targetBranchId,
            @Param("moduleCode") String moduleCode
    );

    List<VisibilityGrant> findByPerson_IdOrderByCreatedAtDesc(UUID personId);
}
