package pe.dcs.app.features.integration.dto;

import pe.dcs.app.features.integration.domain.IntegrationProvider;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class IntegrationDtos {
    private IntegrationDtos() {
    }

    /** Tarjeta de una integración. Nunca lleva valores de secretos: solo {@code secretsConfigured}. */
    public record Card(String provider, String kind, String status, boolean configured, List<IntegrationProvider.Field> fields,
                       Map<String, Object> settings, Map<String, Boolean> secretsConfigured, Instant lastTestAt, Boolean lastTestOk,
                       String lastError, boolean testFresh, boolean canActivate, Long version) {
    }

    public record SaveRequest(Map<String, Object> settings, Map<String, String> secrets) {
    }

    public record RotateRequest(Map<String, String> secrets) {
    }
}
