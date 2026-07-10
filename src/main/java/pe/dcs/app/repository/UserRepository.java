package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.dcs.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByCredentialUsername(
            String username
    );

    boolean existsByDniAndOrganizationIsNull(
            String dni
    );

    boolean existsByDniAndOrganizationIsNullAndIdNot(
            String dni,
            UUID id
    );

    boolean existsByDniAndOrganizationId(
            String dni,
            UUID organizationId
    );

    boolean existsByDniAndOrganizationIdAndIdNot(
            String dni,
            UUID organizationId,
            UUID id
    );

    @Query("""
        SELECT DISTINCT u
        FROM User u
        LEFT JOIN FETCH u.organization
        LEFT JOIN FETCH u.branch
        LEFT JOIN FETCH u.credential c
        LEFT JOIN FETCH u.accesses ua
        LEFT JOIN FETCH ua.organization
        LEFT JOIN FETCH ua.branch
        LEFT JOIN FETCH ua.role
        WHERE c.username = :username
    """)
    Optional<User> findProfileByUsername(
            @Param("username") String username
    );

}