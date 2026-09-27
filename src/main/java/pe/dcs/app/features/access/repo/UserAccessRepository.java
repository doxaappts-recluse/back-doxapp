package pe.dcs.app.features.access.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import pe.dcs.app.features.access.domain.AccessStatus;
import pe.dcs.app.features.access.domain.UserAccess;
import pe.dcs.app.util.enums.RoleType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccessRepository extends JpaRepository<UserAccess, UUID>, JpaSpecificationExecutor<UserAccess> {

    List<UserAccess> findByPersonIdAndOrganizationIdAndStatus(UUID personId, UUID organizationId, AccessStatus status);

    Optional<UserAccess> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<UserAccess> findByPersonIdAndOrganizationId(UUID personId, UUID organizationId);

    long countByOrganizationIdAndRoleAndStatus(UUID organizationId, RoleType role, AccessStatus status);

    /** M02 [V6] · existe al menos un administrador de organización (activo o con invitación pendiente). */
    boolean existsByOrganizationIdAndRoleAndStatusIn(UUID organizationId, RoleType role, java.util.Collection<AccessStatus> statuses);

    /** [V9] unique(person, org, branch, role), tratando la sede nula como un valor. */
    @Query(value = """
            select count(*) > 0 from user_access
             where person_id = :personId and organization_id = :orgId and role = :role
               and branch_id is not distinct from cast(:branchId as uuid)
            """, nativeQuery = true)
    boolean duplicateExists(UUID personId, UUID orgId, UUID branchId, String role);

    /** [V8] administradores de organización ACTIVOS que quedarían sin contar el acceso indicado (uno invitado aún no puede administrar). */
    @Query(value = """
            select count(*) from user_access
             where organization_id = :orgId and role = 'ORG_ADMIN' and status = 'ACTIVE' and id <> :excludedId
            """, nativeQuery = true)
    long otherOrgAdmins(UUID orgId, UUID excludedId);

    /** Licencias comprometidas en la organización: accesos ACTIVE + invitaciones pendientes (MEMBER no consume). */
    @Query(value = """
            select count(*) from user_access
             where organization_id = :orgId and status in ('ACTIVE', 'INVITED')
               and role in ('ORG_ADMIN', 'ORG_BRANCH_ADMIN', 'ORG_USER')
            """, nativeQuery = true)
    long reservedInOrganization(UUID orgId);

    @Query(value = """
            select count(*) from user_access
             where organization_id = :orgId and branch_id = :branchId and status in ('ACTIVE', 'INVITED')
               and role in ('ORG_ADMIN', 'ORG_BRANCH_ADMIN', 'ORG_USER')
            """, nativeQuery = true)
    long reservedInBranch(UUID orgId, UUID branchId);

    /** Accesos con fecha de fin ya cumplida (la organización decide el "hoy" de cada uno). */
    @Query(value = """
            select * from user_access
             where status in ('ACTIVE', 'INVITED') and valid_to is not null and valid_to < current_date + 1
            """, nativeQuery = true)
    List<UserAccess> findExpiryCandidates();
}
