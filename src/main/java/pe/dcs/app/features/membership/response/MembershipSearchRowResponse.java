package pe.dcs.app.features.membership.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.StatusType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Fila del listado de personas con su membresía vigente
 * (si tiene). Coincide con las columnas de la tabla del
 * frontend (MEMBERSHIP_TABLE_COLUMNS).
 */
@Getter
@Setter
public class MembershipSearchRowResponse extends AuditableResponse {

    private UUID id;

    private String name;

    private String lastname;

    private boolean hasMembership;

    private StatusType membershipStatus;

    private String membershipReason;

    private LocalDate membershipStartDate;

    private LocalDate membershipEndDate;

    private String membershipExitReason;

    /**
     * true = existe membresía pero pertenece a otra sede y no hay
     * visibilidad concedida todavía (ver VisibilityGrant); los
     * campos de arriba quedan sin setear en ese caso.
     */
    private boolean restricted;
}
