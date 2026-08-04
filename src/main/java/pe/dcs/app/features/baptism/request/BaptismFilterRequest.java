package pe.dcs.app.features.baptism.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class BaptismFilterRequest {

    private String name;

    private String lastname;

    /**
     * true = solo personas con bautizo registrado.
     * false = solo personas sin bautizo registrado.
     * null = sin filtrar.
     */
    private Boolean hasBaptism;

    private Boolean verified;

    /** Rango sobre Baptism.baptismDate — solo usado por Reportes Avanzados. */
    private LocalDate startDate;

    private LocalDate endDate;

    /**
     * Solo relevante para org admin/SYSTEM; para branch admin/org
     * user delegado el scope ya lo fija BaptismSpecification con la
     * sede actual.
     */
    private UUID branchId;
}
