package pe.dcs.app.features.event.request.registration;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.events.PaymentStatus;
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

    /**
     * Opcional: permite marcar la inscripción como pagada desde la
     * creación (p.ej. cobro en efectivo al momento de inscribir),
     * en vez de crearla PENDING y tener que ir a marcarla pagada
     * aparte. Si se omite, aplica la regla por defecto (ver
     * EventRegistrationServiceImpl.resolvePaymentStatus): gratis
     * (finalPrice=0) siempre PAID, el resto PENDING. Una entrada
     * gratuita siempre queda PAID sin importar lo que se envíe acá.
     */
    private PaymentStatus paymentStatus;

    private String observations;
}