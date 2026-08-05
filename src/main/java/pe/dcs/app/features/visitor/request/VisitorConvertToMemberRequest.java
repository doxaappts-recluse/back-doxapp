package pe.dcs.app.features.visitor.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.membership.MembershipReason;

import java.time.LocalDate;

/**
 * Datos mínimos para abrir la Membership al convertir un visitante
 * en miembro (ver VisitorServiceImpl.convertToMember) — el resto de
 * MembershipFormRequest (exitReason/notes) no aplica acá porque es
 * una membresía nueva, no una edición.
 */
@Getter
@Setter
public class VisitorConvertToMemberRequest {

    @NotNull(message = "{error.fechaInicioMembresiaObligatoria}")
    private LocalDate startDate;

    /**
     * Normalmente MEMBERSHIP, pero se deja configurable por si se
     * quiere abrir como NEW en vez de MEMBERSHIP directo.
     */
    private MembershipReason reason;

    private String notes;
}
