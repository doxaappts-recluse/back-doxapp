package pe.dcs.app.features.church_attendance.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class ChurchServiceAttendanceResponse {

    private UUID id;

    private UUID personId;
    private String name;
    private String dni;
    private boolean member;

    private LocalDate attendanceDate;

    private String observations;
}
