package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import pe.dcs.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    @Query("""
        SELECT u
        FROM User u
        JOIN FETCH u.role
        WHERE u.id = :id
    """)
    Optional<User> findByIdWithRole(UUID id);

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

    boolean existsByDniAndOrganizationId(String dni, UUID organizationId);

    boolean existsByOrganizationIdAndRole_Value(UUID organizationId, String value);

    Optional<User> findByOrganizationIdAndRole_Value(UUID organizationId, String roleValue);

    boolean existsByDniAndOrganizationIdAndIdNot(String dni, UUID organizationId, UUID id);

    @Query("""
        SELECT u
        FROM User u
        LEFT JOIN FETCH u.role
        LEFT JOIN FETCH u.organization
        LEFT JOIN FETCH u.credential
        WHERE u.credential.username = :username
    """)
    Optional<User> findProfileByUsername(String username);
}
