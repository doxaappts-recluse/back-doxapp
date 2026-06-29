package pe.dcs.app.features.event.response.event;

import lombok.Getter;
import lombok.Setter;
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

    private String templateConfig;
}