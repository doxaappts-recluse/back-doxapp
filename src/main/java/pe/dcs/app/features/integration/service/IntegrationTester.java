package pe.dcs.app.features.integration.service;

import pe.dcs.app.features.integration.domain.IntegrationProvider;

import java.util.Map;

/** Prueba de conexión de un proveedor. M19 (correo/SMS/WhatsApp) y M15 (pagos) aportan los suyos. */
public interface IntegrationTester {
    boolean supports(IntegrationProvider provider);

    TestResult test(Map<String, Object> settings, Map<String, String> secrets);
}
