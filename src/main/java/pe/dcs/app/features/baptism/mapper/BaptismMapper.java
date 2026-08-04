package pe.dcs.app.features.baptism.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Baptism;
import pe.dcs.app.entity.Person;
import pe.dcs.app.features.baptism.response.BaptismContextResponse;
import pe.dcs.app.features.baptism.response.BaptismDetailResponse;
import pe.dcs.app.features.baptism.response.BaptismSearchRowResponse;
import pe.dcs.app.features.baptism.response.BaptismUserResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
public class BaptismMapper {

    public BaptismSearchRowResponse toSearchRow(
            Person person,
            Baptism baptism,
            boolean showAudit,
            boolean visible
    ) {

        BaptismSearchRowResponse row = new BaptismSearchRowResponse();

        row.setId(person.getId());
        row.setName(person.getName());
        row.setLastname(person.getLastname());
        row.setHasBaptism(baptism != null);

        if (baptism != null && !visible) {
            row.setRestricted(true);
            return row;
        }

        BaseMapper.mapAudit(baptism, row, showAudit);

        if (baptism != null) {
            row.setBaptismDate(baptism.getBaptismDate());
            row.setChurchName(baptism.getChurchName());
            row.setVerified(baptism.isVerified());
        }

        return row;
    }

    public BaptismDetailResponse toDetailResponse(Baptism baptism) {

        BaptismDetailResponse response = new BaptismDetailResponse();

        response.setId(baptism.getId());
        response.setBaptismDate(baptism.getBaptismDate());
        response.setChurchName(baptism.getChurchName());
        response.setPastorName(baptism.getPastorName());
        response.setCity(baptism.getCity());
        response.setVerified(baptism.isVerified());
        response.setObservations(baptism.getObservations());

        return response;
    }

    public BaptismUserResponse toUserResponse(Person person) {

        BaptismUserResponse response = new BaptismUserResponse();

        response.setId(person.getId());
        response.setName(person.getName());
        response.setLastname(person.getLastname());

        return response;
    }

    public BaptismContextResponse toContextResponse(
            Person person,
            Baptism baptism,
            boolean visible
    ) {

        BaptismContextResponse response = new BaptismContextResponse();

        response.setUser(toUserResponse(person));

        if (baptism != null && !visible) {

            response.setRestricted(true);
            response.setBaptism(null);

            return response;
        }

        response.setBaptism(
                baptism != null
                        ? toDetailResponse(baptism)
                        : null
        );

        return response;
    }
}
