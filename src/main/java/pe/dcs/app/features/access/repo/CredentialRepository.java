package pe.dcs.app.features.access.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pe.dcs.app.features.access.domain.Credential;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository extends JpaRepository<Credential, UUID> {

    /** Credencial de personal de plataforma (sin organización). */
    @Query("select c from Credential c where c.organizationId is null and lower(c.username) = lower(:username)")
    Optional<Credential> findStaffByUsername(String username);

    /** Credencial de una persona dentro de UNA organización (D4: login por organización). */
    @Query("select c from Credential c where c.organizationId = :orgId and lower(c.username) = lower(:username)")
    Optional<Credential> findPersonByUsername(UUID orgId, String username);

    /** Login directo: todas las credenciales (de plataforma y de cualquier organización) con ese usuario. */
    @Query("select c from Credential c where lower(c.username) = lower(:username)")
    List<Credential> findAllByUsername(String username);

    Optional<Credential> findByPersonId(UUID personId);

    Optional<Credential> findByStaffId(UUID staffId);

    List<Credential> findByStaffIdIn(Collection<UUID> staffIds);
}
