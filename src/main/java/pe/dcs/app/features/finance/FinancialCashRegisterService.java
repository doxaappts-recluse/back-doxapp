package pe.dcs.app.features.finance;

import pe.dcs.app.features.finance.request.FinancialCashRegisterCloseRequest;
import pe.dcs.app.features.finance.request.FinancialCashRegisterOpenRequest;
import pe.dcs.app.features.finance.response.FinancialCashRegisterResponse;

import java.util.List;
import java.util.UUID;

public interface FinancialCashRegisterService {

    FinancialCashRegisterResponse open(FinancialCashRegisterOpenRequest request);

    FinancialCashRegisterResponse close(UUID id, FinancialCashRegisterCloseRequest request);

    FinancialCashRegisterResponse getById(UUID id);

    /**
     * Cajas de la organización/sede actual, según el mismo scope que
     * FinancialMovementSpecification: org admin ve todas las sedes
     * de su organización, cualquier otro rol (branch admin u org
     * user delegado) solo ve las de su sede actual.
     */
    List<FinancialCashRegisterResponse> listAll();

    /**
     * La caja OPEN de una sede puntual, si existe — usado por el
     * front para decidir si mostrar "Abrir caja" o "Cerrar caja".
     * Devuelve null si no hay ninguna abierta (no es un error).
     */
    FinancialCashRegisterResponse getOpenByBranch(UUID branchId);
}
