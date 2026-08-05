package pe.dcs.app.features.ministry_role.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.MinistryRole;
import pe.dcs.app.features.ministry_role.response.MinistryRoleResponse;

@Component
public class MinistryRoleMapper {

    public MinistryRoleResponse simple(MinistryRole entity){

        MinistryRoleResponse dto = new MinistryRoleResponse();

        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setNameEs(entity.getNameEs());
        dto.setNameEn(entity.getNameEn());
        dto.setName(entity.getLocalizedName());
        dto.setDescription(entity.getDescription());
        dto.setStatus(entity.getStatus());

        dto.setMinistryId(
                entity.getMinistry().getId()
        );

        dto.setRequiresActiveMembership(
                entity.getRequiresActiveMembership()
        );

        return dto;
    }

}