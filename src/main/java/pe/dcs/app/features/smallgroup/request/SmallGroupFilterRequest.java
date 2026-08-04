package pe.dcs.app.features.smallgroup.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class SmallGroupFilterRequest {

    private String name;

    private String category;

    private String leaderName;

    private StatusType status;

    /** Rango sobre SmallGroup.startDate — solo usado por Reportes Avanzados. */
    private LocalDate startDate;

    private LocalDate endDate;

    /**
     * Solo relevante para org admin/SYSTEM; para branch admin/org
     * user delegado el scope ya lo fija SmallGroupSpecification con
     * la sede/permiso actual.
     */
    private UUID branchId;
}
