package pe.dcs.app.features.document_template;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.DocumentTemplate;
import pe.dcs.app.features.document_template.response.DocumentTemplateResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
public class DocumentTemplateMapper {

    public DocumentTemplateResponse simple(DocumentTemplate template, boolean showAudit) {

        DocumentTemplateResponse response = new DocumentTemplateResponse();

        BaseMapper.mapAudit(template, response, showAudit);

        response.setId(template.getId());
        response.setOrganizationId(template.getOrganization().getId());
        response.setOrganizationName(template.getOrganization().getName());

        if (template.getBranch() != null) {
            response.setBranchId(template.getBranch().getId());
            response.setBranchName(template.getBranch().getName());
        }

        response.setDocumentType(template.getDocumentType().name());
        response.setName(template.getName());
        response.setHasTemplate(template.getTemplatePath() != null);
        response.setTemplateConfig(template.getTemplateConfig());
        response.setStatus(template.getStatus().name());

        return response;
    }
}
