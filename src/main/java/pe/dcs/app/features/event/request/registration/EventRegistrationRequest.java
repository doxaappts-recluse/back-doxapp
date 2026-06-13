package pe.dcs.app.features.event.request.registration;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.events.RegistrationCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class EventRegistrationRequest {

    private UUID eventId;

    private RegistrationCategory category;

    // MEMBER / STAFF / SCHOLARSHIP

    private UUID userId;

    // VISITOR / GUEST
    private String name;

    private String lastname;

    private String phone;

    private String email;

    private LocalDate birthDate;

    private BigDecimal regularPrice;

    private BigDecimal discount;

    private String observations;
}