package pe.dcs.app.features.ministry_role.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.MinistryRole;
import pe.dcs.app.features.ministry_role.response.MinistryRoleResponse;

@Component
public class MinistryRoleMapper {

    public MinistryRoleResponse simple(MinistryRole entity) {

        MinistryRoleResponse dto = new MinistryRoleResponse();

        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setActive(entity.getActive());
        dto.setMinistryId(
                entity.getMinistry() != null ? entity.getMinistry().getId() : null
        );
        dto.setRequiresActiveMembership(entity.getRequiresActiveMembership());
        return dto;
    }
}