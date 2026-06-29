package pe.dcs.app.features.event.request.event;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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
}