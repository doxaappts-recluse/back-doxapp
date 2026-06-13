package pe.dcs.app.features.event.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Event;
import pe.dcs.app.features.event.response.event.EventDetailResponse;
import pe.dcs.app.features.event.response.event.EventResponse;

@Component
public class EventMapper {

    public EventResponse simple(Event event) {

        EventResponse response = new EventResponse();

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

        return response;
    }
}