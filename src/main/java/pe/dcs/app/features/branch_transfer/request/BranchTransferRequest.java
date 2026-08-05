package pe.dcs.app.features.branch_transfer.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class BranchTransferRequest {

    @NotNull(message = "{error.debeIndicarSedeDestino}")
    private UUID targetBranchId;

    // motivo del traslado: opcional, no se valida en el servicio
    private String reason;
}
