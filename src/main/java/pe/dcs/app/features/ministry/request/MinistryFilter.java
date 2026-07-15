package pe.dcs.app.features.ministry.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

@Getter
@Setter
public class MinistryFilter {
    private String name;
    private StatusType status;
}