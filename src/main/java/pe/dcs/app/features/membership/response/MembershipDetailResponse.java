package pe.dcs.app.features.membership.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Un registro de membresía puntual: se usa tanto para
 * "currentMembership" (getCurrent) como para cada fila
 * del historial (history).
 */
@Getter
@Setter
public class MembershipDetailResponse {

    private UUID id;

    private LocalDate startDate;

    private LocalDate endDate;

    private String status;

    private String reason;

    private String exitReason;

    private String notes;

    private boolean current;
}
