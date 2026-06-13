package pe.dcs.app.features.module.request;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ModuleRequest {

    private String name;
    private String code;
    private String icon;
    private String route;
    private Integer orderNum;
    private UUID parentId;

}