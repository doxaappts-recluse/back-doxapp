package pe.dcs.app.features.event.request.registration;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.events.RegistrationCategory;
import pe.dcs.app.util.enums.events.RegistrationStatus;

import java.util.UUID;

@Getter
@Setter
public class EventRegistrationFilter {
    private UUID eventId;
    private RegistrationCategory category;
    private RegistrationStatus status;
    private String name;
    private String lastname;
    private String phone;
}