package pe.dcs.app.features.baptism.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Fila del listado de personas con su bautizo (si tiene).
 * Coincide con las columnas de la tabla del frontend
 * (BAPTISM_TABLE_COLUMNS).
 */
@Getter
@Setter
public class BaptismSearchRowResponse extends AuditableResponse {

    private UUID id;

    private String name;

    private String lastname;

    private boolean hasBaptism;

    private LocalDate baptismDate;

    private String churchName;

    private boolean verified;

    /**
     * true = existe bautizo pero pertenece a otra sede sin
     * visibilidad concedida; los campos de arriba quedan sin
     * setear en ese caso.
     */
    private boolean restricted;
}
