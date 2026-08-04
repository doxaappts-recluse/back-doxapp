package pe.dcs.app.features.visibility.request;

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

    private String moduleCode;

    private UUID sourceBranchId;

    private String reason;

    private LocalDate requestedFrom;

    private LocalDate requestedUntil;
}
