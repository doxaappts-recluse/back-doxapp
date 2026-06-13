package pe.dcs.app.features.event.response.reports;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class RegistrationReportResponse {
    private LocalDate date;
    private long registered;
    private long cancelled;
}