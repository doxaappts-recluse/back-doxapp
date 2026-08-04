package pe.dcs.app.features.visibility.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class VisibilityRequestApproveRequest {

    /**
     * Fecha máxima hasta la que queda visible la data (null =
     * sin fecha límite, hasta que se desactive el permiso).
     */
    private LocalDate approvedUntil;
}
