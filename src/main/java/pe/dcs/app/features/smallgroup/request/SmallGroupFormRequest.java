package pe.dcs.app.features.smallgroup.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class SmallGroupFormRequest {

    private String name;

    private String description;

    /**
     * Temporada del grupo. startDate es obligatoria; endDate null =
     * temporada en curso. Mientras haya un líder vinculado a una
     * Person con sede activa, estas fechas se reflejan en el
     * servicio ministerial generado automáticamente (ver
     * SmallGroupServiceImpl.syncLeaderMinistryService).
     */
    private LocalDate startDate;
    private LocalDate endDate;

    /**
     * Tema a tratar durante la temporada — opcional.
     */
    private String topic;

    /**
     * Setear solo si el líder se encontró por DNI (ver
     * SmallGroupController.findPersonByDni). Si es null, el grupo
     * queda con leaderName en texto libre nada más — el líder no
     * tiene por qué ser un miembro ni tener registro alguno.
     */
    private UUID leaderPersonId;
    private String leaderName;
    private String leaderDni;

    private String meetingDay;

    private String meetingTime;

    private String location;

    private String category;

    private StatusType status;

    /**
     * Solo relevante para org admin (elige sede libremente); igual
     * criterio que el resto de features (Marriage, FinancialMovement).
     */
    private UUID branchId;
}
