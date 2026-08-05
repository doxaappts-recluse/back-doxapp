package pe.dcs.app.features.ministry.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Ministry;
import pe.dcs.app.features.ministry.request.MinistryRequest;
import pe.dcs.app.features.ministry.response.MinistryResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
public class MinistryMapper {

    public MinistryResponse simple(Ministry entity, boolean showAudit){

        MinistryResponse dto = new MinistryResponse();

        BaseMapper.mapAudit(entity, dto, showAudit);

        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setNameEs(entity.getNameEs());
        dto.setNameEn(entity.getNameEn());
        dto.setName(entity.getLocalizedName());
        dto.setDescription(entity.getDescription());
        dto.setStatus(entity.getStatus());

        return dto;
    }

    public void updateEntity(Ministry entity, MinistryRequest request){

        entity.setCode(request.getCode().trim().toUpperCase());
        entity.setNameEs(request.getNameEs());
        entity.setNameEn(request.getNameEn());
        entity.setDescription(request.getDescription());

        if(request.getStatus() != null){
            entity.setStatus(request.getStatus());
        }

    }

}