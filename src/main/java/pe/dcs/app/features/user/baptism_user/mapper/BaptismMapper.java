package pe.dcs.app.features.user.baptism_user.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Baptism;
import pe.dcs.app.features.user.baptism_user.response.BaptismData;
import pe.dcs.app.features.user.baptism_user.response.BaptismDetailResponse;
import pe.dcs.app.features.user.baptism_user.response.BaptismSearchResponse;
import pe.dcs.app.features.user.shared.BaseUserMapper;

@Component
public class BaptismMapper {

    public BaptismDetailResponse detail(Baptism entity) {

        BaptismDetailResponse response =
                new BaptismDetailResponse();

        response.setUser(
                BaseUserMapper.base(entity.getUser())
        );

        BaptismData data =
                new BaptismData();

        data.setId(entity.getId());
        data.setBaptismDate(entity.getBaptismDate());
        data.setChurchName(entity.getChurchName());
        data.setPastorName(entity.getPastorName());
        data.setCity(entity.getCity());
        data.setVerified(entity.isVerified());
        data.setObservations(entity.getObservations());

        response.setBaptism(data);

        return response;
    }

    public BaptismSearchResponse search(Baptism entity) {

        BaptismSearchResponse response =
                new BaptismSearchResponse();

        response.setUser(
                BaseUserMapper.base(entity.getUser())
        );

        response.setHasBaptism(true);
        response.setBaptismDate(entity.getBaptismDate());
        response.setChurchName(entity.getChurchName());
        response.setVerified(entity.isVerified());

        return response;
    }

}