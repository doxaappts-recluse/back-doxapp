package pe.dcs.app.features.module.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ModuleRequest {

    @NotBlank(message = "{error.nombreModuloObligatorio}")
    private String nameEs;

    @NotBlank(message = "{error.nombreModuloObligatorio}")
    private String nameEn;

    @NotBlank(message = "{error.codigoModuloObligatorio}")
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