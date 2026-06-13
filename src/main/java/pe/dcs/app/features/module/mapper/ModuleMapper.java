package pe.dcs.app.features.module.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Module;
import pe.dcs.app.features.module.response.ModuleResponse;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;

@Component
public class ModuleMapper {

    public ModuleResponse base(Module module) {

        ModuleResponse dto = new ModuleResponse();

        dto.setId(module.getId());
        dto.setName(module.getName());
        dto.setCode(module.getCode());
        dto.setIcon(module.getIcon());
        dto.setRoute(module.getRoute());
        dto.setOrderNum(module.getOrderNum());

        dto.setRoot(module.getParent() == null);
        dto.setStatus(module.getStatus() == StatusType.ACTIVE);

        if (module.getParent() != null) {
            dto.setParentId(module.getParent().getId());
            dto.setParentName(module.getParent().getName());
        }

        return dto;
    }

    public ModuleResponse system(Module module, List<ModuleResponse> children) {

        ModuleResponse dto = base(module);

        dto.setSource("SYSTEM");
        dto.setPermissions(List.of("VIEW", "CREATE", "EDIT", "DELETE", "DOWNLOAD"));
        dto.setChildren(children);

        return dto;
    }

    public ModuleResponse organization(
            Module module,
            List<String> permissions,
            List<ModuleResponse> children
    ) {

        ModuleResponse dto = base(module);

        dto.setSource("ORGANIZATION");
        dto.setPermissions(permissions);
        dto.setChildren(children);

        return dto;
    }

    public ModuleResponse simple(Module module) {

        ModuleResponse dto = base(module);

        dto.setPermissions(List.of());
        dto.setChildren(List.of());
        dto.setSource(null);

        return dto;
    }

}