package pe.dcs.app.features.church_attendance.service;

import pe.dcs.app.features.church_attendance.request.ChurchServiceAttendanceFormRequest;
import pe.dcs.app.features.church_attendance.request.ChurchServiceFormRequest;
import pe.dcs.app.features.church_attendance.request.ChurchServiceSearchRequest;
import pe.dcs.app.features.church_attendance.response.ChurchPersonSearchResponse;
import pe.dcs.app.features.church_attendance.response.ChurchServiceAttendanceResponse;
import pe.dcs.app.features.church_attendance.response.ChurchServiceDetailResponse;
import pe.dcs.app.features.church_attendance.response.ChurchServiceSearchRowResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ChurchServiceService {

    PageResponse<ChurchServiceSearchRowResponse> search(ChurchServiceSearchRequest request);

    ChurchServiceDetailResponse getById(UUID id);

    ChurchPersonSearchResponse findPersonByDni(String dni);

    UUID create(ChurchServiceFormRequest request);

    void update(UUID id, ChurchServiceFormRequest request);

    List<ChurchServiceAttendanceResponse> listAttendance(UUID churchServiceId, LocalDate date);

    void markAttendance(UUID churchServiceId, ChurchServiceAttendanceFormRequest request);

    void removeAttendance(UUID churchServiceId, UUID attendanceId);
}
