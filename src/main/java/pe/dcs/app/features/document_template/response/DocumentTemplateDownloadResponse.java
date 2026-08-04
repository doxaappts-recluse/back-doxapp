package pe.dcs.app.features.document_template.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta del endpoint de descarga: URL firmada de la plantilla
 * RAW (sin datos pintados) + su config de posiciones — el backend
 * nunca compone el documento final, eso lo hace el front (ver
 * TemplateComposerService.ts). Mismo shape que
 * TicketTemplateResponse (tickets de Eventos), a propósito, para
 * reusar el mismo patrón de consumo en el front.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTemplateDownloadResponse {

    private String templateUrl;

    private JsonNode templateConfig;
}
