package pe.dcs.app.features.module.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Module;
import pe.dcs.app.features.module.response.ModuleResponse;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;

@Component
public class ModuleMapper {

    public ModuleResponse simple(Module module){

        ModuleResponse dto = new ModuleResponse();

        dto.setId(module.getId());
        dto.setName(module.getName());
        dto.setCode(module.getCode());
        dto.setIcon(module.getIcon());
        dto.setRoute(module.getRoute());
        dto.setOrderNum(module.getOrderNum());

        dto.setStatus(module.isActive());

        dto.setRoot(module.isRoot());

        if(module.getParent() != null){
            dto.setParentId(module.getParent().getId());
            dto.setParentName(module.getParent().getName());
        }

        /*
         * Este mapper es únicamente
         * para CRUD.
         *
         * Los permisos y el árbol del
         * sidebar los construye
         * SidebarMapper.
         */

        dto.setPermissions(List.of());
        dto.setChildren(List.of());
        dto.setSource(null);

        return dto;
    }

}