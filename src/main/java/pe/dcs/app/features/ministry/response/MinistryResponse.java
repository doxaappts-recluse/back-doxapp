package pe.dcs.app.features.ministry.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class MinistryResponse {
    private UUID id;
    private String name;
    private String description;
    private Boolean active;
}