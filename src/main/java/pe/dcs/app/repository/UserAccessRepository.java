package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.UserAccess;
import pe.dcs.app.util.enums.RoleType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAccessRepository extends JpaRepository<UserAccess, UUID>, JpaSpecificationExecutor<UserAccess> {

    boolean existsByOrganizationIdAndRoleValue(
            UUID organizationId,
            String roleValue
    );

    Optional<UserAccess> findByOrganizationIdAndRoleValue(
            UUID organizationId,
            RoleType role
    );

    @Query("""
        SELECT ua
        FROM UserAccess ua
        JOIN FETCH ua.organization o
        LEFT JOIN FETCH ua.branch b
        JOIN FETCH ua.role r
        WHERE ua.person.id = :personId
        AND ua.active = true
    """)
    List<UserAccess> findActiveAccessesByPerson(
            @Param("personId") UUID personId
    );
}
