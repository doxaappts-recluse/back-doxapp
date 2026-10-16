package pe.dcs.app.features.audit.dto;

import java.util.List;

/** Valores que ofrece el visor para sus filtros: módulos y acciones que existen en el alcance de quien consulta. */
public record AuditFilterOptions(List<ModuleOption> modules, List<String> actions, List<String> entityTypes) {

    public record ModuleOption(String code, String nameEs, String nameEn) {
    }
}
