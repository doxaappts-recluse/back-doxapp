package pe.dcs.app.features.person.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pe.dcs.app.shared.vo.DocumentType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonRepository extends JpaRepository<Person, UUID> {

    Optional<Person> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Person> findByOrganizationIdAndDocTypeAndDocNumber(UUID organizationId, DocumentType docType, String docNumber);

    /** M05 · búsqueda mínima para elegir a quién dar un acceso (M06 traerá la búsqueda completa). */
    @Query("""
            select p from Person p where p.organizationId = :orgId and p.status = 'ACTIVE' and (
                lower(p.docNumber) like :like or lower(p.firstName) like :like or lower(p.lastName) like :like
                or lower(concat(p.firstName, ' ', p.lastName)) like :like or lower(coalesce(p.email, '')) like :like)
            order by p.lastName, p.firstName
            """)
    List<Person> searchMinimal(UUID orgId, String like, org.springframework.data.domain.Pageable pageable);
}
