package pe.dcs.app.features.dashboard.widget;

import pe.dcs.app.security.AuthenticatedActor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Lo que un proveedor necesita para calcular su indicador. branchId solo aplica a N2/N3. */
public record WidgetContext(AuthenticatedActor actor, LocalDate from, LocalDate to, UUID branchId, Instant now) {
}
