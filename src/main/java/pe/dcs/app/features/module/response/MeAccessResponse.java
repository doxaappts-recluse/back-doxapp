package pe.dcs.app.features.module.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.SystemRoleType;

import java.util.List;

@Getter
@Setter
public class MeAccessResponse {

    private List<ModuleResponse> modules;
    private SystemRoleType accessType;

}