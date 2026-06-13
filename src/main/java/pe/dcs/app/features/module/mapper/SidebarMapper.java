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
            UUID contractId
    ) {

        Map<UUID, List<Module>> grouped =
                modules.stream()
                        .filter(m -> m.getParent() != null)
                        .collect(Collectors.groupingBy(m -> m.getParent().getId()));

        return modules.stream()
                .filter(m -> m.getParent() == null)
                .map(m -> map(m, grouped, allowedModuleIds, contractId))
                .filter(Objects::nonNull)
                .toList();
    }

    private ModuleResponse map(
            Module node,
            Map<UUID, List<Module>> grouped,
            Set<UUID> allowedModuleIds,
            UUID contractId
    ) {

        List<Module> children = grouped.getOrDefault(node.getId(), List.of());

        List<ModuleResponse> mappedChildren =
                children.stream()
                        .map(c -> map(c, grouped, allowedModuleIds, contractId))
                        .filter(Objects::nonNull)
                        .toList();

        boolean isAllowed = allowedModuleIds == null
                || allowedModuleIds.contains(node.getId())
                || !mappedChildren.isEmpty();

        if (!isAllowed) return null;

        ModuleResponse dto = new ModuleResponse();

        dto.setId(node.getId());
        dto.setName(node.getName());
        dto.setCode(node.getCode());
        dto.setIcon(node.getIcon());
        dto.setRoute(node.getRoute());
        dto.setOrderNum(node.getOrderNum());

        dto.setPermissions(
                contractId != null
                        ? permissionService.getPermissions(contractId, node.getId())
                        : List.of()
        );

        dto.setChildren(mappedChildren);

        return dto;
    }
}