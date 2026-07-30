package pe.dcs.app.features.event.request.event;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.events.EventScope;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
public class EventRequest {

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private LocalDateTime startDateTime;

    @NotNull
    private LocalDateTime endDateTime;

    @NotBlank
    private String location;

    private BigDecimal price;

    private Integer capacity;

    private Integer goal;

    private BigDecimal expectedBudget;

    private JsonNode templateConfig;

    /**
     * Solo lo usa el org admin (un branch admin siempre crea/edita
     * en scope BRANCH sobre su propia sede, sin importar lo que
     * mande acá; ver EventServiceImpl.resolveScope). Si viene null,
     * se asume ORGANIZATION.
     */
    private EventScope scope;

    /**
     * Requerido solo cuando scope = BRANCH y quien llama es org
     * admin (un branch admin no necesita mandarlo, se resuelve
     * solo con su sede actual).
     */
    private UUID branchId;
}