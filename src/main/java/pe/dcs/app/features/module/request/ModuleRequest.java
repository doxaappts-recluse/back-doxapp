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

    /*
     * Visibilidad por rol. Si vienen null (frontend viejo
     * que todavía no manda el campo), el service los trata
     * como true para no ocultar módulos por accidente.
     */
    private Boolean visibleSystem;
    private Boolean visibleOrgAdmin;
    private Boolean visibleBranchAdmin;
    private Boolean visibleUser;

}