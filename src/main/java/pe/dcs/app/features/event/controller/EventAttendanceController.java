package pe.dcs.app.features.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.event.request.attendance.CheckInRequest;
import pe.dcs.app.features.event.response.attendance.CheckInResponse;
import pe.dcs.app.features.event.service.EventAttendanceService;
import pe.dcs.app.util.ApiResponse;

@RestController
@RequestMapping("/api/v1/event-attendance")
@RequiredArgsConstructor
public class EventAttendanceController {

    private final EventAttendanceService attendanceService;

    @PostMapping("/check-in")
    public ApiResponse<CheckInResponse> checkIn(
            @RequestBody CheckInRequest request
    ) {
        return new ApiResponse<>(
                200,
                "Asistencia registrada correctamente",
                attendanceService.checkIn(request)
        );
    }
}