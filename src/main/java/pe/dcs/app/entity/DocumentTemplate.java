package pe.dcs.app.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.DocumentTemplateType;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

/**
 * Plantilla RAW (imagen) de un documento configurable por la
 * organización (certificado de bautizo, certificado de donación,
 * otros) — el módulo "Plantillas de Documentos" solo sube y sirve
 * la plantilla cruda vía URL firmada; NUNCA compone el documento
 * final acá (eso lo hace el front, client-side, pintando
 * nombre/fecha/monto sobre la imagen — ver
 * TemplateComposerService.ts). Un fondo/módulo consumidor solo
 * consulta {@link pe.dcs.app.features.document_template.DocumentTemplateService#download}.
 */
@Entity
@Table(
        name = "document_templates",
        indexes = {
                @Index(
                        name = "idx_document_template_organization",
                        columnList = "organization_id"
                )
        }
)
@Getter
@Setter
public class DocumentTemplate extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "organization_id",
            nullable = false
    )
    private Organization organization;

    /**
     * Opcional: null = plantilla válida para toda la organización;
     * si se define, solo aplica a esa sede (una sede puede tener su
     * propia plantilla distinta a la de la organización).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "document_type")
    private DocumentTemplateType documentType;

    @Column(nullable = false)
    private String name;

    @Column(name = "template_path")
    private String templatePath;

    /**
     * Posiciones de los campos de texto a pintar en el front
     * (nombre/fecha/monto), en el mismo formato porcentual que ya
     * usa Event.templateConfig para el QR de tickets — ver
     * TicketDesignerComponent/TemplateComposerService.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_config", columnDefinition = "jsonb")
    private JsonNode templateConfig;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;
}
