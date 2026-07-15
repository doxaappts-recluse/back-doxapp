package pe.dcs.app.features.ministry.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

@Getter
@Setter
public class MinistryRequest {

    @NotBlank
    private String name;
    private String description;
    private StatusType status = StatusType.ACTIVE;

}