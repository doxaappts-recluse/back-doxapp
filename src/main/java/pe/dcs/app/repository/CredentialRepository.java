package pe.dcs.app.repository;

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
    Optional<Credential> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndUserIdNot(
            String username,
            UUID userId
    );

    long countByUserOrganizationIdAndStatus(
            UUID organizationId,
            StatusType status
    );

    long countByUser_Organization_IdAndStatus(
            UUID organizationId,
            StatusType status
    );

    boolean existsByUsernameAndIdNot(
            String username,
            UUID userId
    );
}

