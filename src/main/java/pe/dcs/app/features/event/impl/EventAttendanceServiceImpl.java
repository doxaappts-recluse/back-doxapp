package pe.dcs.app.features.event.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Event;
import pe.dcs.app.entity.EventAttendance;
import pe.dcs.app.entity.EventRegistration;
import pe.dcs.app.features.event.request.attendance.CheckInRequest;
import pe.dcs.app.features.event.response.attendance.CheckInResponse;
import pe.dcs.app.features.event.service.EventAttendanceService;
import pe.dcs.app.repository.EventAttendanceRepository;
import pe.dcs.app.repository.EventRegistrationRepository;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.events.EventStatus;
import pe.dcs.app.util.enums.events.RegistrationStatus;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
@Transactional
public class EventAttendanceServiceImpl implements EventAttendanceService {

    private final EventRegistrationRepository registrationRepository;
    private final EventAttendanceRepository attendanceRepository;

    @Transactional
    public CheckInResponse checkIn(CheckInRequest request) {

        String token = extractToken(request.getToken());

        // 1. Buscar inscripción por QR
        EventRegistration registration =
                registrationRepository.findByQrToken(token)
                        .orElseThrow(() ->
                                new Exceptions("QR inválido", HttpStatus.NOT_FOUND)
                        );

        // 2. Validar evento activo
        Event event = registration.getEvent();

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new Exceptions("Evento no activo", HttpStatus.BAD_REQUEST);
        }

        // 3. Validar inscripción válida
        if (registration.getStatus() != RegistrationStatus.REGISTERED) {
            throw new Exceptions("Inscripción inválida", HttpStatus.BAD_REQUEST);
        }

        // 4. ANTI FRAUDE: ya tiene asistencia (tu modelo es 1–1)
        if (registration.getAttendance() != null) {
            throw new Exceptions(
                    "El usuario ya registró asistencia",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 5. ANTI FRAUDE: QR reutilizado
        if (Boolean.TRUE.equals(registration.getQrUsed())) {
            throw new Exceptions(
                    "QR ya utilizado",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 6. Crear asistencia
        EventAttendance attendance = new EventAttendance();
        attendance.setRegistration(registration);
        attendance.setAttendedAt(LocalDateTime.now());

        attendanceRepository.save(attendance);

        // 7. Marcar QR como usado (IMPORTANTE)
        registration.setQrUsed(true);
        registration.setQrUsedAt(LocalDateTime.now());

        registrationRepository.save(registration);

        return new CheckInResponse(
                registration.getId(),
                registration.getName(),
                registration.getLastname(),
                attendance.getAttendedAt()
        );
    }

    // =========================
    // HELPERS
    // =========================

    private String extractToken(String input) {

        if (input == null || input.isBlank()) {
            throw new Exceptions("Token vacío", HttpStatus.BAD_REQUEST);
        }

        if (input.contains("token=")) {
            return input.substring(input.indexOf("token=") + 6);
        }

        return input.trim();
    }
}