package pe.dcs.app.features.event.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Event;
import pe.dcs.app.entity.EventRegistration;
import pe.dcs.app.features.event.response.event.TicketResponse;
import pe.dcs.app.features.event.service.ticket.TicketGeneratorService;
import pe.dcs.app.features.event.service.ticket.TicketService;
import pe.dcs.app.repository.EventRegistrationRepository;
import pe.dcs.app.service.supabase.StorageBucketResolver;
import pe.dcs.app.service.supabase.SupabaseStorageService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.StorageBucket;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final EventRegistrationRepository registrationRepository;
    private final TicketGeneratorService generatorService;

    private final SupabaseStorageService storageService;
    private final StorageBucketResolver bucketResolver;

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

    @Override
    @Transactional(readOnly = true)
    public TicketResponse getTicket(UUID registrationId) {

        EventRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() ->
                        new Exceptions(
                                "Inscripción no encontrada",
                                HttpStatus.NOT_FOUND
                        )
                );

        Event event = registration.getEvent();
        System.out.println("TemplatePath: " + event.getTemplatePath());
        String templateUrl = storageService.createSignedUrlFull(
                bucketResolver.resolve(StorageBucket.EVENTS),
                event.getTemplatePath(),
                300
        );

        return TicketResponse.builder()
                .templateUrl(templateUrl)
                .build();
    }
}