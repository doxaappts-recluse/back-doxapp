package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.UserAccessModule;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAccessModuleRepository extends JpaRepository<UserAccessModule, UUID> {

    /**
     * Módulos delegados a una persona, acotados al acceso puntual
     * (organización + sede) actualmente activo.
     *
     * Necesario porque una persona puede tener varios UserAccess
     * (uno por sede/rol): sin este filtro, un módulo delegado bajo
     * el acceso de UNA sede podía "filtrarse" hacia el sidebar de
     * OTRA sede si el contrato de esa otra sede también lo permitía.
     */
    @Query("""
        SELECT uam.module.id
        FROM UserAccessModule uam
        WHERE uam.userAccess.person.id = :personId
          AND uam.userAccess.organization.id = :organizationId
          AND uam.userAccess.branch.id = :branchId
          AND uam.userAccess.active = :status
          AND uam.status = 'ACTIVE'
          AND uam.enabled = true
    """)
    List<UUID> findActiveModuleIdsByPersonIdAndOrganizationIdAndBranchId(
            @Param("personId") UUID personId,
            @Param("organizationId") UUID organizationId,
            @Param("branchId") UUID branchId,
            @Param("status") StatusType status
    );

    // =========================================================
    // ADMINISTRACION (asignación de accesos)
    // =========================================================

    List<UserAccessModule> findByUserAccessId(
            UUID userAccessId
    );

    void deleteByUserAccessId(
            UUID userAccessId
    );

}