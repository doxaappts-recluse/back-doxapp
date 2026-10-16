package pe.dcs.app.features.branch.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import pe.dcs.app.util.enums.StatusType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID>, JpaSpecificationExecutor<Branch> {

    List<Branch> findByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);

    List<Branch> findByOrganizationId(UUID organizationId);

    Optional<Branch> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** M02 [V6] · la organización tiene su sede principal activa. */
    boolean existsByOrganizationIdAndMainTrueAndStatus(UUID organizationId, StatusType status);

    Optional<Branch> findByOrganizationIdAndMainTrueAndStatus(UUID organizationId, StatusType status);

    long countByOrganizationIdAndStatus(UUID organizationId, StatusType status);

    /** [V1] nombre único por organización sin distinguir mayúsculas. */
    @Query("select count(b) > 0 from Branch b where b.organizationId = :orgId and lower(b.name) = lower(:name) and (:excludeId is null or b.id <> :excludeId)")
    boolean nameTaken(UUID orgId, String name, UUID excludeId);

    /** [V2] código único por organización (el mismo código en otra organización es válido). */
    @Query("select count(b) > 0 from Branch b where b.organizationId = :orgId and b.code = :code and (:excludeId is null or b.id <> :excludeId)")
    boolean codeTaken(UUID orgId, String code, UUID excludeId);

    /** [V7] personas con acceso ACTIVE asignado a la sede (una persona con varios roles cuenta una vez). */
    @Query(value = "select count(distinct person_id) from user_access where organization_id = :orgId and branch_id = :branchId and status = 'ACTIVE'",
            nativeQuery = true)
    long activePeople(UUID orgId, UUID branchId);

    /** [V7] contratos de alcance BRANCH de esa sede que siguen vigentes (ACTIVE o SUSPENDED). */
    @Query(value = "select count(*) from contract where organization_id = :orgId and scope = 'BRANCH' and branch_id = :branchId and status in ('ACTIVE', 'SUSPENDED')",
            nativeQuery = true)
    long activeBranchContracts(UUID orgId, UUID branchId);

    /** [V6] tope de sedes del contrato ACTIVE (nulo si no hay contrato o no fija tope). Si hay varios, el mayor. */
    @Query(value = "select max(max_branches) from contract where organization_id = :orgId and status = 'ACTIVE' and scope = 'ORGANIZATION'",
            nativeQuery = true)
    Integer contractMaxBranches(UUID orgId);

    /** Organización dueña de la sede, sin cargar la entidad (para bloquear la organización antes de leerla). */
    @Query("select b.organizationId from Branch b where b.id = :id")
    Optional<UUID> organizationIdOf(UUID id);
}
