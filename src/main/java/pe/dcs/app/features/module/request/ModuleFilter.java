package pe.dcs.app.features.module.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ModuleFilter {

    private String name;
    private String code;
    private Boolean status;
}