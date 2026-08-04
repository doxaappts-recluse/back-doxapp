package pe.dcs.app.features.finance.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Una fila del listado de Donantes: agrega todos los movimientos
 * INCOME/APROBADOS de una persona (o del "donante anónimo" — los
 * registrados sin personId). Ver
 * FinancialMovementServiceImpl.donors().
 */
@Getter
@Setter
public class FinancialDonorResponse {

    private UUID personId;
    private String personName;
    private String personLastname;
    private String dni;

    /**
     * true = esta fila agrupa todos los movimientos registrados sin
     * donante identificado (personId null). En ese caso
     * personId/personName/personLastname/dni/member quedan sin
     * setear.
     */
    private boolean anonymous;

    /** ¿Es miembro activo de la iglesia? Solo aplica si !anonymous. */
    private boolean member;

    private BigDecimal totalIncome;
    private long movementCount;
}
