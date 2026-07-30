package pe.dcs.app.features.event.response.event;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.events.EventScope;
import pe.dcs.app.util.enums.events.EventStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class EventDetailResponse {

    private UUID id;

    private String name;

    private String description;

    private LocalDateTime startDateTime;

    private LocalDateTime endDateTime;

    private String location;

    private BigDecimal price;

    private Integer capacity;

    private Integer goal;

    private BigDecimal expectedBudget;

    private EventStatus status;

    private JsonNode templateConfig;

    private EventScope scope;

    private UUID branchId;

    private String branchName;

    /**
     * Org admin siempre; branch admin solo si es la sede
     * coordinadora; org user delegado solo si él creó el evento y
     * tiene EDIT. Gatea en el front el acceso a
     * dashboard/reportes/asistencia y los botones de gestión.
     */
    private boolean canManage;
}