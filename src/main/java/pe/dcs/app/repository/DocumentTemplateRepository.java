package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.DocumentTemplate;
import pe.dcs.app.util.enums.DocumentTemplateType;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, UUID> {

    /** Catálogo completo de la organización — solo org admin, ver DocumentTemplateServiceImpl.listAll(). */
    List<DocumentTemplate> findByOrganizationIdOrderByNameAsc(UUID organizationId);

    /**
     * Catálogo acotado a una sede — branch admin y org user
     * delegado solo ven/gestionan lo de su propia sede (ver
     * DocumentTemplateServiceImpl.listAll() / DocumentTemplateAccessGuard).
     */
    List<DocumentTemplate> findByOrganizationIdAndBranchIdOrderByNameAsc(
            UUID organizationId,
            UUID branchId
    );

    /**
     * Todas las plantillas ACTIVAS de un tipo en la organización
     * (org-wide + por sede) — el service elige la más específica
     * (ver DocumentTemplateServiceImpl.download): prioriza la que
     * tenga branchId == sede actual, si no existe usa la org-wide
     * (branchId null).
     */
    List<DocumentTemplate> findByOrganizationIdAndDocumentTypeAndStatus(
            UUID organizationId,
            DocumentTemplateType documentType,
            StatusType status
    );
}
