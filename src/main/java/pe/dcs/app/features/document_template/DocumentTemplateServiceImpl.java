package pe.dcs.app.features.document_template;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.DocumentTemplate;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.features.document_template.request.DocumentTemplateRequest;
import pe.dcs.app.features.document_template.response.DocumentTemplateDownloadResponse;
import pe.dcs.app.features.document_template.response.DocumentTemplateResponse;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.DocumentTemplateRepository;
import pe.dcs.app.repository.OrganizationRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.service.supabase.StorageBucketResolver;
import pe.dcs.app.service.supabase.SupabaseStorageService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.StorageBucket;
import pe.dcs.app.util.enums.DocumentTemplateType;
import pe.dcs.app.util.enums.StatusType;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentTemplateServiceImpl implements DocumentTemplateService {

    private final DocumentTemplateRepository documentTemplateRepository;
    private final OrganizationRepository organizationRepository;
    private final BranchRepository branchRepository;
    private final AuthContext authContext;
    private final DocumentTemplateAccessGuard accessGuard;
    private final DocumentTemplateMapper documentTemplateMapper;

    private final SupabaseStorageService storageService;
    private final StorageBucketResolver bucketResolver;

    private UUID currentOrganizationId() {

        UUID organizationId = authContext.getCurrentOrganizationId();

        if (organizationId == null) {
            throw new Exceptions(
                    "No se pudo determinar la organización actual",
                    HttpStatus.BAD_REQUEST
            );
        }

        return organizationId;
    }

    private DocumentTemplate findOwn(UUID id) {

        DocumentTemplate template =
                documentTemplateRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Plantilla no encontrada",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (!template.getOrganization().getId().equals(currentOrganizationId())) {

            throw new Exceptions(
                    "No tiene acceso a esta plantilla",
                    HttpStatus.FORBIDDEN
            );
        }

        return template;
    }

    private String buildPath(UUID orgId, UUID templateId) {
        return orgId + "/" + templateId + "/template.png";
    }

    @Override
    @Transactional
    public DocumentTemplateResponse create(DocumentTemplateRequest request, MultipartFile file) {

        accessGuard.assertCanCreate();

        Organization organization =
                organizationRepository.findById(currentOrganizationId())
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Organización no encontrada",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        DocumentTemplate template = new DocumentTemplate();

        template.setOrganization(organization);
        template.setDocumentType(request.getDocumentType());
        template.setName(request.getName());
        template.setTemplateConfig(request.getTemplateConfig());
        template.setStatus(StatusType.ACTIVE);

        UUID branchId = accessGuard.resolveBranchId(request.getBranchId());

        if (branchId != null) {
            template.setBranch(findBranch(branchId, organization.getId()));
        }

        // 1. persistir primero para obtener ID
        DocumentTemplate saved = documentTemplateRepository.save(template);

        // 2. upload opcional
        if (file != null && !file.isEmpty()) {

            String path = buildPath(organization.getId(), saved.getId());

            try (InputStream input = file.getInputStream()) {

                storageService.upload(
                        input,
                        bucketResolver.resolve(StorageBucket.DOCUMENT_TEMPLATES),
                        path,
                        file.getContentType()
                );

                // 3. actualizar entity (dirty checking)
                saved.setTemplatePath(path);

            } catch (IOException e) {
                throw new RuntimeException("Error uploading document template image", e);
            }
        }

        return documentTemplateMapper.simple(saved, authContext.canViewAudit());
    }

    @Override
    @Transactional
    public DocumentTemplateResponse update(UUID id, DocumentTemplateRequest request, MultipartFile file) {

        DocumentTemplate template = findOwn(id);

        accessGuard.assertCanManage(template);

        template.setDocumentType(request.getDocumentType());
        template.setName(request.getName());
        template.setTemplateConfig(request.getTemplateConfig());
        template.setUpdatedAt(Instant.now());

        UUID branchId = accessGuard.resolveBranchId(request.getBranchId());

        if (branchId != null) {
            template.setBranch(findBranch(branchId, template.getOrganization().getId()));
        } else {
            template.setBranch(null);
        }

        // upload opcional (overwrite)
        if (file != null && !file.isEmpty()) {

            String path = buildPath(template.getOrganization().getId(), template.getId());

            try (InputStream input = file.getInputStream()) {

                storageService.upload(
                        input,
                        bucketResolver.resolve(StorageBucket.DOCUMENT_TEMPLATES),
                        path,
                        file.getContentType()
                );

                template.setTemplatePath(path);

            } catch (IOException e) {
                throw new RuntimeException("Error updating document template image", e);
            }
        }

        return documentTemplateMapper.simple(
                documentTemplateRepository.save(template),
                authContext.canViewAudit()
        );
    }

    private Branch findBranch(UUID branchId, UUID organizationId) {

        Branch branch =
                branchRepository.findById(branchId)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Sede no encontrada",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (!branch.getOrganization().getId().equals(organizationId)) {
            throw new Exceptions(
                    "La sede no pertenece a la organización actual",
                    HttpStatus.BAD_REQUEST
            );
        }

        return branch;
    }

    @Override
    @Transactional
    public DocumentTemplateResponse enable(UUID id) {

        DocumentTemplate template = findOwn(id);

        accessGuard.assertCanManage(template);

        template.setStatus(StatusType.ACTIVE);
        template.setUpdatedAt(Instant.now());

        return documentTemplateMapper.simple(
                documentTemplateRepository.save(template),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional
    public DocumentTemplateResponse disable(UUID id) {

        DocumentTemplate template = findOwn(id);

        accessGuard.assertCanManage(template);

        template.setStatus(StatusType.INACTIVE);
        template.setUpdatedAt(Instant.now());

        return documentTemplateMapper.simple(
                documentTemplateRepository.save(template),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentTemplateResponse getById(UUID id) {

        DocumentTemplate template = findOwn(id);

        accessGuard.assertCanManage(template);

        return documentTemplateMapper.simple(
                template,
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentTemplateResponse> listAll() {

        accessGuard.assertCanUse();

        boolean showAudit = authContext.canViewAudit();

        UUID organizationId = currentOrganizationId();

        /*
         * Org admin ve el catálogo completo de su organización
         * (org-wide + todas las sedes). Branch admin/org user
         * delegado solo ven lo de su propia sede — la org-wide es
         * decisión de organización, no de sede (ver
         * DocumentTemplateAccessGuard).
         */
        List<DocumentTemplate> templates =
                authContext.isCurrentOrganizationAdmin()
                        ? documentTemplateRepository.findByOrganizationIdOrderByNameAsc(organizationId)
                        : documentTemplateRepository.findByOrganizationIdAndBranchIdOrderByNameAsc(
                                organizationId,
                                authContext.getCurrentBranchId()
                        );

        return templates.stream()
                .map(t -> documentTemplateMapper.simple(t, showAudit))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentTemplateDownloadResponse download(DocumentTemplateType documentType) {

        UUID organizationId = currentOrganizationId();
        UUID branchId = authContext.getCurrentBranchId();

        List<DocumentTemplate> candidates =
                documentTemplateRepository.findByOrganizationIdAndDocumentTypeAndStatus(
                        organizationId,
                        documentType,
                        StatusType.ACTIVE
                );

        // prioriza la plantilla específica de la sede actual; si no
        // existe, cae a la plantilla org-wide (branch == null)
        DocumentTemplate template = candidates.stream()
                .filter(t -> t.getTemplatePath() != null)
                .filter(t -> branchId != null
                        && t.getBranch() != null
                        && t.getBranch().getId().equals(branchId))
                .findFirst()
                .or(() -> candidates.stream()
                        .filter(t -> t.getTemplatePath() != null)
                        .filter(t -> t.getBranch() == null)
                        .findFirst())
                .orElseThrow(() ->
                        new Exceptions(
                                "No hay una plantilla configurada para este tipo de documento",
                                HttpStatus.NOT_FOUND
                        )
                );

        String templateUrl = storageService.createSignedUrlFull(
                bucketResolver.resolve(StorageBucket.DOCUMENT_TEMPLATES),
                template.getTemplatePath(),
                300
        );

        return DocumentTemplateDownloadResponse.builder()
                .templateUrl(templateUrl)
                .templateConfig(template.getTemplateConfig())
                .build();
    }
}
