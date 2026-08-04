package pe.dcs.app.features.document_template.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.DocumentTemplateType;

import java.util.UUID;

@Getter
@Setter
public class DocumentTemplateRequest {

    private DocumentTemplateType documentType;

    private String name;

    /**
     * Opcional: si se define, la plantilla solo aplica a esa sede.
     * Sin definir, es la plantilla org-wide para ese documentType.
     * Solo el org admin puede fijarlo libremente — un branch admin
     * u org user delegado siempre terminan con la sede de su propio
     * contexto, sin importar lo que envíen acá (ver
     * DocumentTemplateAccessGuard.resolveBranchId()).
     */
    private UUID branchId;

    /**
     * Posiciones de los campos de texto (nombre/fecha/monto) a
     * pintar en el front — mismo formato que
     * TicketDesignerComponent produce para el QR de tickets, solo
     * que acá son campos de texto en vez de un QR.
     */
    private JsonNode templateConfig;
}
