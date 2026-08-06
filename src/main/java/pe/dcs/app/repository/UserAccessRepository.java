package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.UserAccess;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAccessRepository extends JpaRepository<UserAccess, UUID>, JpaSpecificationExecutor<UserAccess> {

    @Query("""
        SELECT ua
        FROM UserAccess ua
        WHERE ua.person.id = :personId
          AND ua.active = :status
    """)
    List<UserAccess> findActiveAccessesByPerson(
            @Param("personId") UUID personId,
            @Param("status") StatusType status
    );

    boolean existsByPersonIdAndOrganizationIdAndBranchIdAndRoleId(
            UUID personId,
            UUID organizationId,
            UUID branchId,
            UUID roleId
    );

    Optional<UserAccess> findByPersonIdAndOrganizationIdAndBranchIdAndRoleId(
            UUID personId,
            UUID organizationId,
            UUID branchId,
            UUID roleId
    );

    List<UserAccess> findByPersonId(
            UUID personId
    );

    // =========================================================
    // LICENCIAS: conteo de accesos activos por org/sede
    // =========================================================

    /**
     * Accesos activos de TODA la organización, sin importar
     * sede (incluye ORG_ADMIN con branch null). Usado contra
     * el maxLicenses de un contrato scope=ORGANIZATION.
     */
    long countByOrganizationIdAndActive(
            UUID organizationId,
            StatusType status
    );

    /**
     * Accesos activos de UNA sede puntual. Usado contra el
     * maxLicenses de un contrato scope=BRANCH, o contra el
     * allocatedLicenses de esa sede en modo ALLOCATED.
     */
    long countByBranchIdAndActive(
            UUID branchId,
            StatusType status
    );
}