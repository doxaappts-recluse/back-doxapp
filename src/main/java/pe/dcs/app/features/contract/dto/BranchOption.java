package pe.dcs.app.features.contract.dto;

import java.util.UUID;

/** Sede de una organización, para elegir alcance y reparto en el formulario de contrato (N1 no gestiona sedes: eso es M04). */
public record BranchOption(UUID id, String name, String code, boolean main, String status) {
}
