package pe.dcs.app.features.config.dto;

import java.time.LocalDate;
import java.util.List;

public final class OrgProfileDtos {
    private OrgProfileDtos() {
    }

    public record Holiday(LocalDate date, String name) {
    }

    public record Response(String country, String timezone, String currency, String defaultLanguage, String dateFormat,
                           Integer fiscalYearStartMonth, List<String> workingDays, List<Holiday> holidays, boolean canEdit,
                           boolean currencyChanged, Long version) {
    }

    public record Request(String country, String timezone, String currency, String defaultLanguage, String dateFormat,
                          Integer fiscalYearStartMonth, List<String> workingDays, List<Holiday> holidays,
                          Boolean confirmChange, Long version) {
    }
}
