package pe.dcs.app.features.organization.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID>, JpaSpecificationExecutor<Organization> {

    @Query("select o from Organization o where lower(o.slug) = lower(:slug)")
    Optional<Organization> findBySlug(String slug);

    /** [V3] slug único sin distinguir mayúsculas; {@code excludeId} para ignorar la propia organización al editar. */
    @Query("select count(o) > 0 from Organization o where lower(o.slug) = lower(:slug) and (:excludeId is null or o.id <> :excludeId)")
    boolean slugTaken(String slug, UUID excludeId);

    /** [V2] RUC único. */
    @Query("select count(o) > 0 from Organization o where o.taxId = :taxId and (:excludeId is null or o.id <> :excludeId)")
    boolean taxIdTaken(String taxId, UUID excludeId);

    /** M04 · bloquea la fila de la organización para serializar altas, cambio de principal y tope de sedes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Organization o where o.id = :id")
    Optional<Organization> lockById(UUID id);
}
