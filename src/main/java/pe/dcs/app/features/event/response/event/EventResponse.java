package pe.dcs.app.features.event.response.event;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.events.EventStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class EventResponse {

    private UUID id;

    private String name;

    private LocalDateTime startDateTime;

    private LocalDateTime endDateTime;

    private String location;

    private BigDecimal price;

    private Integer capacity;

    private Integer goal;

    private EventStatus status;
}