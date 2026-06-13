package pe.dcs.app.features.event.request.registration;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EventRegistrationBulkRequest {

    private List<EventRegistrationRequest> registrations;
}