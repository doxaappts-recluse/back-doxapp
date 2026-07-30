package pe.dcs.app.features.event.response.event;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.events.EventScope;
import pe.dcs.app.util.enums.events.EventStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class EventResponse extends AuditableResponse {

    private UUID id;

    private String name;

    private LocalDateTime startDateTime;

    private LocalDateTime endDateTime;

    private String location;

    private BigDecimal price;

    private Integer capacity;

    private Integer goal;

    private EventStatus status;

    private EventScope scope;

    private UUID branchId;

    private String branchName;

    /**
     * ¿Quien consulta puede GESTIONAR este evento (editar,
     * publicar/cancelar, ver dashboard/reportes/asistencia)? Org
     * admin siempre; branch admin solo si es la sede coordinadora.
     * El frontend usa esto para ocultar los botones de gestión de
     * eventos que solo puede ver/inscribir pero no administrar.
     */
    private boolean canManage;
}