package pe.dcs.app.features.module.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

@Getter
@Setter
public class ModuleFilter {

    private String name;
    private String code;
    private StatusType status;
}