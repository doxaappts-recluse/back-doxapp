package pe.dcs.app.features.ministerial_service.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.MinistryAssignment;
import pe.dcs.app.entity.Person;
import pe.dcs.app.features.ministerial_service.response.MinisterialServiceResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
public class MinisterialServiceMapper {

    public MinisterialServiceResponse toSearchRow(Person person, MinistryAssignment current, boolean showAudit) {

        MinisterialServiceResponse row = new MinisterialServiceResponse();

        BaseMapper.mapAudit(current, row, showAudit);

        row.setId(person.getId());
        row.setName(person.getName());
        row.setLastname(person.getLastname());
        row.setHasMinistry(current != null);

        return row;
    }

}
