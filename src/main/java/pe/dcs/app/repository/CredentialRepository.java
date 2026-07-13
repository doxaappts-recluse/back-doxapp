package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.Credential;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CredentialRepository extends JpaRepository<Credential, UUID> {

    @Query("""
    SELECT DISTINCT c
        FROM Credential c
        JOIN FETCH c.person p
        LEFT JOIN FETCH p.accesses ua
        LEFT JOIN FETCH ua.role r
        LEFT JOIN FETCH ua.organization o
        LEFT JOIN FETCH ua.branch b
        WHERE c.username = :username
    """)
    Optional<Credential> findFullAccessByUsername(
            @Param("username") String username
    );

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(
            String username,
            UUID id
    );

}