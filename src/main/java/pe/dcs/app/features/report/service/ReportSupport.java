package pe.dcs.app.features.report.service;

import org.springframework.http.HttpStatus;
import pe.dcs.app.features.report.dto.ReportDtos;
import pe.dcs.app.util.Exceptions;

import java.time.LocalDate;
import java.time.Period;

/** M20 · Constantes y validaciones comunes del motor de reportes. */
public final class ReportSupport {

    public static final String MODULE = "REPORTS";
    public static final String PLATFORM_MODULE = "PLATFORM_REPORTS";
    public static final int MAX_EXPORT_ROWS = 50_000;
    public static final int MAX_SCHEDULES_PER_ORG = 20;

    private ReportSupport() {
    }

    /** [V2] el rango de un reporte no puede superar 5 años; por defecto, si no se especifica, el año en curso. */
    public static ReportDtos.RunFilters normalize(ReportDtos.RunFilters f, java.time.Clock clock) {
        LocalDate today = LocalDate.now(clock);
        LocalDate to = f == null || f.to() == null ? today : f.to();
        LocalDate from = f == null || f.from() == null ? to.withDayOfYear(1) : f.from();
        if (from.isAfter(to)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "rango de fechas");
        }
        if (Period.between(from, to).toTotalMonths() > 60) {
            throw new Exceptions("error.report.rangeTooLong", HttpStatus.UNPROCESSABLE_ENTITY);                                         // [V2]
        }
        return new ReportDtos.RunFilters(f == null ? null : f.branchIds(), from, to);
    }

    public static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
