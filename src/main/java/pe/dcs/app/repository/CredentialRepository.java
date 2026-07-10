package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.Credential;
import pe.dcs.app.entity.User;
import pe.dcs.app.util.enums.StatusType;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CredentialRepository extends JpaRepository<Credential, UUID> {

    @Query("""
        SELECT DISTINCT c
        FROM Credential c
        JOIN FETCH c.user u
        LEFT JOIN FETCH u.organization
        LEFT JOIN FETCH u.branch
        LEFT JOIN FETCH u.accesses ua
        LEFT JOIN FETCH ua.organization
        LEFT JOIN FETCH ua.branch
        LEFT JOIN FETCH ua.role
        WHERE c.username = :username
    """)
    Optional<Credential> findFullAccessByUsername(
            @Param("username") String username
    );

    Optional<Credential> findByUsername(
            String username
    );

    boolean existsByUsername(
            String username
    );

    boolean existsByUsernameAndIdNot(
            String username,
            UUID credentialId
    );

    /**
     * Cantidad de credenciales de usuarios
     * pertenecientes a una organización
     */
    long countByUser_Organization_IdAndStatus(
            UUID organizationId,
            StatusType status
    );

    /**
     * Verifica si una credencial tiene un rol
     * dentro de una organización.
     *
     * Ahora el rol vive en UserAccess.
     */
    @Query("""
        SELECT CASE WHEN COUNT(ua) > 0 THEN true ELSE false END
        FROM Credential c
        JOIN c.user u
        JOIN u.accesses ua
        JOIN ua.role r
        WHERE c.id = :credentialId
        AND ua.organization.id = :organizationId
        AND r.value = :roleValue
    """)
    boolean hasRoleInOrganization(
            @Param("credentialId") UUID credentialId,
            @Param("organizationId") UUID organizationId,
            @Param("roleValue") String roleValue
    );

    /**
     * Obtiene credencial por usuario y rol dentro del contexto.
     */
    @Query("""
        SELECT DISTINCT c
        FROM Credential c
        JOIN FETCH c.user u
        JOIN FETCH u.accesses ua
        JOIN FETCH ua.role r
        WHERE ua.organization.id = :organizationId
        AND r.value = :roleValue
    """)
    Optional<Credential> findByOrganizationAndRole(
            @Param("organizationId") UUID organizationId,
            @Param("roleValue") String roleValue
    );

}