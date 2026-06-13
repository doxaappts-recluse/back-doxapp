package pe.dcs.app.features.event.request.finance;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EventFinanceRejectRequest {

    @NotBlank
    private String reason;
}