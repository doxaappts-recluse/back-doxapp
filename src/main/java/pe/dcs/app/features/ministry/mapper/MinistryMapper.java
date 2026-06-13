package pe.dcs.app.features.ministry.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Ministry;
import pe.dcs.app.features.ministry.response.MinistryResponse;

@Component
public class MinistryMapper {

    public MinistryResponse simple(Ministry entity) {
        MinistryResponse dto = new MinistryResponse();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setActive(entity.getActive());
        return dto;
    }
}