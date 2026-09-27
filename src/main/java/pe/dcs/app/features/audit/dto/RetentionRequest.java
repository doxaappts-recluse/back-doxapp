package pe.dcs.app.features.audit.dto;

/** moduleCode nulo = toda la organización (o el valor por defecto de plataforma). */
public record RetentionRequest(String moduleCode, Integer retainMonths) {
}
