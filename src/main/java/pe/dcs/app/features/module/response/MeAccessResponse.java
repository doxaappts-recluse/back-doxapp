package pe.dcs.app.features.module.response;

import pe.dcs.app.util.enums.RoleType;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MeAccessResponse {

    private List<ModuleResponse> modules;
    private RoleType accessType;

}