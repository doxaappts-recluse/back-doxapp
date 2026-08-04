package pe.dcs.app.features.finance;

import pe.dcs.app.features.finance.request.FinancialFundRequest;
import pe.dcs.app.features.finance.response.FinancialFundResponse;

import java.util.List;
import java.util.UUID;

public interface FinancialFundService {

    FinancialFundResponse create(FinancialFundRequest request);

    FinancialFundResponse update(UUID id, FinancialFundRequest request);

    FinancialFundResponse enable(UUID id);

    FinancialFundResponse disable(UUID id);

    /**
     * Detalle de un fondo puntual, para precargar el formulario de
     * edición (misma regla de acceso que update/enable/disable).
     */
    FinancialFundResponse getById(UUID id);

    /**
     * Fondos activos de la organización actual — para poblar el
     * select al registrar un movimiento (branch admin/org user
     * delegado también pueden listarlos, aunque no puedan crearlos).
     */
    List<FinancialFundResponse> listActive();

    /**
     * Todos los fondos (activos e inactivos) de la organización
     * actual — para la pantalla de administración del catálogo.
     */
    List<FinancialFundResponse> listAll();
}
