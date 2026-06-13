package pe.dcs.app.features.event.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pe.dcs.app.entity.Event;
import pe.dcs.app.entity.EventRegistration;
import pe.dcs.app.features.event.service.ticket.TicketGeneratorService;
import pe.dcs.app.features.event.service.ticket.TicketService;
import pe.dcs.app.repository.EventRegistrationRepository;
import pe.dcs.app.util.Exceptions;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final EventRegistrationRepository registrationRepository;
    private final TicketGeneratorService generatorService;

    @Override
    public byte[] generateTicket(UUID registrationId) {

        EventRegistration reg = registrationRepository.findById(registrationId)
                .orElseThrow(() ->
                        new Exceptions(
                                "Inscripcion no encontrada",
                                HttpStatus.NOT_FOUND
                        )
                );

        Event event = reg.getEvent();

        return generatorService.generate(
                event,
                reg.getQrToken()
        );
    }
}