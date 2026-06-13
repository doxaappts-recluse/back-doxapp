package pe.dcs.app.features.permission.mapper;

import pe.dcs.app.entity.Permission;
import pe.dcs.app.features.permission.response.PermissionResponse;

public class PermissionMapper {

    private PermissionMapper(){}

    public static PermissionResponse toResponse(
            Permission permission
    ) {

        PermissionResponse dto =
                new PermissionResponse();

        dto.setId(permission.getId());

        dto.setCode(permission.getCode());

        dto.setName(permission.getName());

        return dto;
    }
}