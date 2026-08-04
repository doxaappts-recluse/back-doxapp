package pe.dcs.app.features.document_template;

import org.springframework.web.multipart.MultipartFile;
import pe.dcs.app.features.document_template.request.DocumentTemplateRequest;
import pe.dcs.app.features.document_template.response.DocumentTemplateDownloadResponse;
import pe.dcs.app.features.document_template.response.DocumentTemplateResponse;
import pe.dcs.app.util.enums.DocumentTemplateType;

import java.util.List;
import java.util.UUID;

public interface DocumentTemplateService {

    DocumentTemplateResponse create(DocumentTemplateRequest request, MultipartFile file);

    DocumentTemplateResponse update(UUID id, DocumentTemplateRequest request, MultipartFile file);

    DocumentTemplateResponse enable(UUID id);

    DocumentTemplateResponse disable(UUID id);

    DocumentTemplateResponse getById(UUID id);

    List<DocumentTemplateResponse> listAll();

    /**
     * Endpoint consumidor (p.ej. detalle de Donante): entrega
     * SOLAMENTE la URL firmada de la plantilla RAW + su config de
     * posiciones — nunca compone el documento final acá, eso es
     * responsabilidad exclusiva del front (ver
     * TemplateComposerService.ts). Resuelve primero la plantilla
     * específica de la sede actual del usuario (si existe) y si no
     * cae a la plantilla org-wide (branch = null) del mismo
     * documentType.
     */
    DocumentTemplateDownloadResponse download(DocumentTemplateType documentType);
}
