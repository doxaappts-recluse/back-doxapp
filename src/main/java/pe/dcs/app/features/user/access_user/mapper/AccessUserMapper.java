package pe.dcs.app.features.user.access_user.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Credential;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.UserAccess;
import pe.dcs.app.features.module.response.ContractModuleAccessResponse;
import pe.dcs.app.features.user.access_user.response.AccessUserConfigResponse;
import pe.dcs.app.features.user.access_user.response.AccessUserModuleConfigResponse;
import pe.dcs.app.features.user.access_user.response.AccessUserPermissionConfigResponse;
import pe.dcs.app.features.user.access_user.response.AccessUserResponse;
import pe.dcs.app.util.auditable.BaseMapper;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class AccessUserMapper {

    /**
     * "access" es el UserAccess ORG_USER puntual que se está
     * listando: la sede/organización que se muestra es la del
     * acceso, no la del historial (PersonBranch) de la persona.
     */
    public AccessUserResponse toResponse(UserAccess access, boolean showAudit) {

        Person person = access.getPerson();
        Credential credential = person.getCredential();

        AccessUserResponse response = AccessUserResponse
                .builder()
                .id(access.getId())
                .personId(person.getId())
                .name(person.getName())
                .lastname(person.getLastname())
                .username(
                        credential != null
                                ? credential.getUsername()
                                : null
                )
                .hasCredential(credential != null)
                .credentialActive(
                        credential != null
                                && credential.getStatus() == StatusType.ACTIVE
                )
                .accessActive(access.getActive() == StatusType.ACTIVE)
                .organizationId(
                        access.getOrganization() != null
                                ? access.getOrganization().getId()
                                : null
                )
                .organizationName(
                        access.getOrganization() != null
                                ? access.getOrganization().getName()
                                : null
                )
                .branchId(
                        access.getBranch() != null
                                ? access.getBranch().getId()
                                : null
                )
                .branchName(
                        access.getBranch() != null
                                ? access.getBranch().getName()
                                : null
                )
                .build();

        BaseMapper.mapAudit(access, response, showAudit);

        return response;
    }

    /**
     * Combina el catálogo de módulos/permisos disponibles del
     * contrato activo (ya filtrado) con lo que el acceso tiene
     * asignado actualmente, marcando "assigned" en cada uno.
     */
    public AccessUserConfigResponse toConfigResponse(
            UserAccess access,
            List<ContractModuleAccessResponse> available,
            Set<UUID> assignedModuleIds,
            java.util.Map<UUID, Set<UUID>> assignedPermissionsByModule
    ) {

        Person person = access.getPerson();
        Credential credential = person.getCredential();

        AccessUserConfigResponse response = new AccessUserConfigResponse();

        response.setId(access.getId());
        response.setPersonId(person.getId());
        response.setName(person.getName());
        response.setLastname(person.getLastname());
        response.setDni(person.getDni());
        response.setUsername(credential != null ? credential.getUsername() : null);
        response.setBranchId(access.getBranch() != null ? access.getBranch().getId() : null);
        response.setBranchName(access.getBranch() != null ? access.getBranch().getName() : null);

        response.setModules(
                available.stream()
                        .map(module -> toModuleConfig(
                                module,
                                assignedModuleIds,
                                assignedPermissionsByModule
                        ))
                        .toList()
        );

        return response;
    }

    private AccessUserModuleConfigResponse toModuleConfig(
            ContractModuleAccessResponse module,
            Set<UUID> assignedModuleIds,
            java.util.Map<UUID, Set<UUID>> assignedPermissionsByModule
    ) {

        AccessUserModuleConfigResponse response = new AccessUserModuleConfigResponse();

        Set<UUID> assignedPermissions =
                assignedPermissionsByModule.getOrDefault(
                        module.getModuleId(),
                        Set.of()
                );

        response.setModuleId(module.getModuleId());
        response.setName(module.getName());
        response.setAssigned(assignedModuleIds.contains(module.getModuleId()));

        response.setPermissions(
                module.getPermissions()
                        .stream()
                        .map(permission ->
                                new AccessUserPermissionConfigResponse(
                                        permission.getId(),
                                        permission.getCode(),
                                        permission.getName(),
                                        assignedPermissions.contains(permission.getId())
                                )
                        )
                        .toList()
        );

        return response;
    }

}
