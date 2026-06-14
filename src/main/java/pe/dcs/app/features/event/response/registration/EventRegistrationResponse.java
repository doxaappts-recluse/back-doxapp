package pe.dcs.app.features.event.response.registration;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.events.RegistrationCategory;
import pe.dcs.app.util.enums.events.RegistrationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class EventRegistrationResponse extends AuditableResponse {

    private UUID id;

    private UUID eventId;

    private String eventName;

    private RegistrationCategory category;

    private UUID userId;

    private String name;

    private String lastname;

    private LocalDate birthDate;

    private String phone;

    private String email;

    private String qrToken;

    private RegistrationStatus status;

    private BigDecimal regularPrice;

    private BigDecimal discount;

    private BigDecimal finalPrice;
}