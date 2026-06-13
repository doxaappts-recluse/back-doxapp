package pe.dcs.app.features.event.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.EventAttendance;
import pe.dcs.app.entity.EventRegistration;
import pe.dcs.app.features.event.response.attendance.TicketValidationResponse;
import pe.dcs.app.features.event.service.ticket.TicketValidationService;
import pe.dcs.app.repository.EventAttendanceRepository;
import pe.dcs.app.repository.EventRegistrationRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class TicketValidationServiceImpl implements TicketValidationService {

    private final EventRegistrationRepository registrationRepository;
    private final EventAttendanceRepository attendanceRepository;

    @Override
    public TicketValidationResponse validate(String qrToken) {

        // 1. Buscar registro
        EventRegistration reg = registrationRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new RuntimeException("QR inválido"));

        // 2. Si ya fue usado → bloquear
        if (Boolean.TRUE.equals(reg.getQrUsed())) {

            return new TicketValidationResponse(
                    false,
                    "La entrada ya fue registrada anteriormente",
                    reg.getId(),
                    reg.getEvent().getId(),
                    reg.getEvent().getName(),
                    getAttendeeName(reg),
                    reg.getQrUsedAt(),
                    true,
                    false
            );
        }

        LocalDateTime now = LocalDateTime.now();

        // 3. Marcar QR como usado
        reg.setQrUsed(true);
        reg.setQrUsedAt(now);

        // 4. Crear attendance si no existe
        boolean alreadyHasAttendance =
                attendanceRepository.existsByRegistrationId(reg.getId());

        if (!alreadyHasAttendance) {

            EventAttendance attendance = new EventAttendance();
            attendance.setRegistration(reg);
            attendance.setAttendedAt(now);

            attendanceRepository.save(attendance);
        }

        // 5. persistir registro
        registrationRepository.save(reg);

        // 6. response
        return new TicketValidationResponse(
                true,
                "Registro correcto",
                reg.getId(),
                reg.getEvent().getId(),
                reg.getEvent().getName(),
                getAttendeeName(reg),
                now,
                false,
                !alreadyHasAttendance
        );
    }

    private String getAttendeeName(EventRegistration reg) {
        if (reg.getUser() != null) {
            return reg.getUser().getName();
        }
        return reg.getName() + " " + reg.getLastname();
    }
}