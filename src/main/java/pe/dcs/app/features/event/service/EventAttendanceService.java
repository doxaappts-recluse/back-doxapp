package pe.dcs.app.features.event.service;

import pe.dcs.app.features.event.request.attendance.CheckInRequest;
import pe.dcs.app.features.event.response.attendance.CheckInResponse;

public interface EventAttendanceService {
    CheckInResponse checkIn(CheckInRequest request);
}