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
            List<UUID> contractIds,
            UUID userId
    ){
        return toTree(modules, allowedModuleIds, contractIds, userId, null, null);
    }

    /**
     * Árbol de sidebar para SYSTEM (SYSTEM_ADMIN/SYSTEM_SUPPORT).
     * No administra bajo un contrato (no hay contractId), así que
     * no tiene sentido acotar sus permisos a
     * contract_module_permissions: siempre ve el catálogo completo
     * de permisos activos en cada módulo visible para su rol.
     */
    public List<ModuleResponse> toTreeForSystem(
            List<Module> modules
    ){

        List<String> allPermissions = permissionService.getAllPermissionCodes();

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
                .map(m -> mapSystem(m, grouped, allPermissions))
                .sorted(
                        Comparator.comparing(
                                ModuleResponse::getOrderNum
                        )
                )
                .toList();
    }

    private ModuleResponse mapSystem(
            Module node,
            Map<UUID, List<Module>> grouped,
            List<String> allPermissions
    ){

        List<ModuleResponse> children =
                grouped.getOrDefault(
                                node.getId(),
                                List.of()
                        )
                        .stream()
                        .map(child -> mapSystem(child, grouped, allPermissions))
                        .sorted(
                                Comparator.comparing(
                                        ModuleResponse::getOrderNum
                                )
                        )
                        .toList();

        ModuleResponse dto = new ModuleResponse();

        dto.setId(node.getId());
        dto.setName(node.getLocalizedName());
        dto.setNameEs(node.getNameEs());
        dto.setNameEn(node.getNameEn());
        dto.setCode(node.getCode());
        dto.setIcon(node.getIcon());
        dto.setRoute(node.getRoute());
        dto.setOrderNum(node.getOrderNum());
        dto.setPermissions(allPermissions);
        dto.setChildren(children);

        return dto;
    }

    /**
     * Overload usado por ORG_USER: además del userId, necesita
     * organizationId/branchId para acotar los permisos delegados
     * al acceso puntual actualmente activo (una persona puede
     * tener otros accesos, en otras sedes, con otros permisos).
     */
    public List<ModuleResponse> toTree(
            List<Module> modules,
            Set<UUID> allowedModuleIds,
            List<UUID> contractIds,
            UUID userId,
            UUID organizationId,
            UUID branchId
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
                                contractIds,
                                userId,
                                organizationId,
                                branchId
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
            List<UUID> contractIds,
            UUID userId,
            UUID organizationId,
            UUID branchId
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
                                        contractIds,
                                        userId,
                                        organizationId,
                                        branchId
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
        dto.setName(node.getLocalizedName());
        dto.setNameEs(node.getNameEs());
        dto.setNameEn(node.getNameEn());
        dto.setCode(node.getCode());
        dto.setIcon(node.getIcon());
        dto.setRoute(node.getRoute());
        dto.setOrderNum(node.getOrderNum());

        /*
         * ADMIN -> permisos del contrato (unión de todos los
         *          contratos activos de la sede: puede haber uno
         *          de ORGANIZATION y otro de BRANCH a la vez)
         * USER  -> permisos asignados al usuario
         */
        if(userId == null){

            dto.setPermissions(
                    permissionService.getPermissions(
                            contractIds,
                            node.getId()
                    )
            );

        }else{

            dto.setPermissions(
                    permissionService.getUserPermissions(
                            userId,
                            organizationId,
                            branchId,
                            node.getId()
                    )
            );

        }

        dto.setChildren(children);

        return dto;
    }

}