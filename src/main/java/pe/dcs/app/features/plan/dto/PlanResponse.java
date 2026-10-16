package pe.dcs.app.features.plan.dto;

import pe.dcs.app.features.plan.domain.PlanStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code contracts}: contratos (en cualquier estado) creados con este plan; {@code allowedStatuses}: transiciones válidas. */
public record PlanResponse(
        UUID id, String code, String name, String description, BigDecimal price, String currency, PlanStatus status,
        List<ModuleRef> modules, long contracts, List<PlanStatus> allowedStatuses, Instant createdAt, Instant updatedAt, Long version
) {
    public record ModuleRef(String code, String nameEs, String nameEn, String status) {
    }
}
