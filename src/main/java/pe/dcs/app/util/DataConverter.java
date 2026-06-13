package pe.dcs.app.util;

import pe.dcs.app.constant.GeneralConstant;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;

public class DataConverter {

    private DataConverter() {
    }

    public static String formatTextTrim(String text) {

        return text.trim();
    }

    public static String formatTextTrimAndUpper(String text) {

        return text.trim().toUpperCase();
    }

    public static String formatTextTrimAndLower(String text) {

        return text.trim().toLowerCase();
    }

    public static String getFirstLetterAndUpperFromText(String text) {

        return text.trim().substring(0, 1).toUpperCase();
    }

    public static String getMonthTextFromTextDate(String textDate) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(GeneralConstant.PATTERN_FECHA_SLASH);

        LocalDate localDate = LocalDate.parse(textDate, formatter);

        int month = localDate.getMonthValue();

        return replaceMonthToText(month);
    }

    public static String getPeriodDiffTextFromDates(LocalDate consultaFecha, LocalDate currentFecha) {

        Period period = Period.between(consultaFecha, currentFecha);

        if (period.getYears() > 0) {
            return period.getYears() + (period.getYears() == 1 ? " AÑO" : " AÑOS");
        } else if (period.getMonths() > 0) {
            return period.getMonths() + (period.getMonths() == 1 ? " MES" : " MESES");
        } else {
            return period.getDays() + (period.getDays() == 1 ? " DÍA" : " DÍAS");
        }
    }

    public static String replaceMonthToText(int month) {

        String textMonth;

        switch (month) {
            case 1 -> textMonth = "ENERO";
            case 2 -> textMonth = "FEBRERO";
            case 3 -> textMonth = "MARZO";
            case 4 -> textMonth = "ABRIL";
            case 5 -> textMonth = "MAYO";
            case 6 -> textMonth = "JUNIO";
            case 7 -> textMonth = "JULIO";
            case 8 -> textMonth = "AGOSTO";
            case 9 -> textMonth = "SETIEMBRE";
            case 10 -> textMonth = "OCTUBRE";
            case 11 -> textMonth = "NOVIEMBRE";
            case 12 -> textMonth = "DICIEMBRE";
            default -> textMonth = "";
        }

        return textMonth;
    }
}
