package pe.dcs.app.features.event.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Event;
import pe.dcs.app.features.event.response.event.EventDetailResponse;
import pe.dcs.app.features.event.response.event.EventResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
public class EventMapper {

    public EventResponse simple(Event event, boolean showAudit) {

        EventResponse response = new EventResponse();

        BaseMapper.mapAudit(event, response, showAudit);

        response.setId(event.getId());
        response.setName(event.getName());
        response.setStartDateTime(
                event.getStartDateTime()
        );
        response.setEndDateTime(
                event.getEndDateTime()
        );
        response.setLocation(
                event.getLocation()
        );
        response.setPrice(
                event.getPrice()
        );
        response.setCapacity(
                event.getCapacity()
        );
        response.setGoal(
                event.getGoal()
        );
        response.setStatus(
                event.getStatus()
        );

        response.setScope(event.getScope());

        if (event.getBranch() != null) {
            response.setBranchId(event.getBranch().getId());
            response.setBranchName(event.getBranch().getName());
        }

        return response;
    }

    public EventDetailResponse detail(
            Event event
    ) {

        EventDetailResponse response =
                new EventDetailResponse();

        response.setId(event.getId());
        response.setName(event.getName());
        response.setDescription(
                event.getDescription()
        );
        response.setStartDateTime(
                event.getStartDateTime()
        );
        response.setEndDateTime(
                event.getEndDateTime()
        );
        response.setLocation(
                event.getLocation()
        );
        response.setPrice(
                event.getPrice()
        );
        response.setGoal(
                event.getGoal()
        );
        response.setCapacity(
                event.getCapacity()
        );
        response.setExpectedBudget(
                event.getExpectedBudget()
        );
        response.setStatus(
                event.getStatus()
        );

        response.setTemplateConfig(event.getTemplateConfig());

        response.setScope(event.getScope());

        if (event.getBranch() != null) {
            response.setBranchId(event.getBranch().getId());
            response.setBranchName(event.getBranch().getName());
        }

        return response;
    }
}