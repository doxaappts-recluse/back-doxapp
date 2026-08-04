package pe.dcs.app.features.document_template.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;

import java.util.UUID;

@Getter
@Setter
public class DocumentTemplateResponse extends AuditableResponse {

    private UUID id;

    private UUID organizationId;
    private String organizationName;

    private UUID branchId;
    private String branchName;

    private String documentType;

    private String name;

    /** true si ya se subió la imagen de la plantilla. */
    private boolean hasTemplate;

    private JsonNode templateConfig;

    private String status;
}
