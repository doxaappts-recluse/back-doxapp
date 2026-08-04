package pe.dcs.app.features.event.response.dashboard;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class RegistrationDashboard {

    private long totalRegistrations;
    private long totalCancelled;
    private long totalActive;
    private double occupancyRate;

    /**
     * Suma de finalPrice de inscripciones pagadas (no canceladas).
     * Independiente del Balance de Finanzas — deliberadamente no
     * se mezclan para evitar doble conteo.
     */
    private BigDecimal registrationIncome;
}