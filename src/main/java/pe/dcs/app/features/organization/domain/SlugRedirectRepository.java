package pe.dcs.app.features.organization.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SlugRedirectRepository extends JpaRepository<SlugRedirect, String> {

    /** Redirección vigente para un slug antiguo (ya en minúsculas). */
    Optional<SlugRedirect> findByOldSlugAndUntilAtAfter(String oldSlug, Instant now);

    /** Slug antiguo aún reservado por OTRA organización (no se puede tomar mientras dure su redirección). */
    @org.springframework.data.jpa.repository.Query("select count(r) > 0 from SlugRedirect r where r.oldSlug = :slug and r.untilAt > :now and (:orgId is null or r.organizationId <> :orgId)")
    boolean reservedByOther(String slug, Instant now, UUID orgId);

    java.util.List<SlugRedirect> findByOrganizationIdAndUntilAtAfterOrderByUntilAtDesc(UUID organizationId, Instant now);

    void deleteByOldSlugAndOrganizationId(String oldSlug, UUID organizationId);
}
