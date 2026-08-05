package pe.dcs.app.features.visibility.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Solicitud de una sede (la sede actual del que llama) para poder
 * ver la data histórica de un módulo, dueña de OTRA sede por
 * donde la persona pasó antes.
 */
@Getter
@Setter
public class VisibilityRequestCreateRequest {

    @NotBlank(message = "{error.elModuloEsObligatorio}")
    private String moduleCode;

    @NotNull(message = "{error.sedeOrigenObligatoria}")
    private UUID sourceBranchId;

    // motivo de la solicitud: opcional, no se valida en el servicio
    private String reason;

    // rango de visibilidad: opcional (sin fecha límite si se omite)
    private LocalDate requestedFrom;

    private LocalDate requestedUntil;
}
