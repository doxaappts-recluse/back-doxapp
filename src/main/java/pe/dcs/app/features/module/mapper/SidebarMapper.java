package pe.dcs.app.features.module.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Module;
import pe.dcs.app.features.module.response.ModuleResponse;
import pe.dcs.app.features.module.service.PermissionService;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SidebarMapper {

    private final PermissionService permissionService;

    public List<ModuleResponse> toTree(
            List<Module> modules,
            Set<UUID> allowedModuleIds,
            UUID contractId,
            UUID userId
    ){

        Map<UUID, List<Module>> grouped =
                modules.stream()
                        .filter(m -> m.getParent() != null)
                        .collect(
                                Collectors.groupingBy(
                                        m -> m.getParent().getId()
                                )
                        );

        return modules.stream()
                .filter(m -> m.getParent() == null)
                .map(m ->
                        map(
                                m,
                                grouped,
                                allowedModuleIds,
                                contractId,
                                userId
                        )
                )
                .filter(Objects::nonNull)
                .sorted(
                        Comparator.comparing(
                                ModuleResponse::getOrderNum
                        )
                )
                .toList();
    }

    private ModuleResponse map(
            Module node,
            Map<UUID, List<Module>> grouped,
            Set<UUID> allowedModuleIds,
            UUID contractId,
            UUID userId
    ){

        List<ModuleResponse> children =
                grouped.getOrDefault(
                                node.getId(),
                                List.of()
                        )
                        .stream()
                        .map(child ->
                                map(
                                        child,
                                        grouped,
                                        allowedModuleIds,
                                        contractId,
                                        userId
                                )
                        )
                        .filter(Objects::nonNull)
                        .sorted(
                                Comparator.comparing(
                                        ModuleResponse::getOrderNum
                                )
                        )
                        .toList();

        boolean moduleAllowed =
                allowedModuleIds == null
                        || allowedModuleIds.contains(node.getId());

        /*
         * Si no tiene acceso al módulo
         * y tampoco tiene hijos visibles,
         * desaparece completamente.
         */
        if(!moduleAllowed && children.isEmpty()){
            return null;
        }

        ModuleResponse dto = new ModuleResponse();

        dto.setId(node.getId());
        dto.setName(node.getName());
        dto.setCode(node.getCode());
        dto.setIcon(node.getIcon());
        dto.setRoute(node.getRoute());
        dto.setOrderNum(node.getOrderNum());

        /*
         * ADMIN -> permisos del contrato
         * USER  -> permisos asignados al usuario
         */
        if(userId == null){

            dto.setPermissions(
                    contractId != null
                            ? permissionService.getPermissions(
                            contractId,
                            node.getId()
                    )
                            : List.of()
            );

        }else{

            dto.setPermissions(
                    permissionService.getUserPermissions(
                            userId,
                            node.getId()
                    )
            );

        }

        dto.setChildren(children);

        return dto;
    }

}