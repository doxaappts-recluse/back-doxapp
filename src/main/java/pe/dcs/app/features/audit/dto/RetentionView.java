package pe.dcs.app.features.audit.dto;

import java.util.List;

/**
 * Política de retención de la auditoría. effective = el mayor entre lo configurado y el mínimo legal del módulo (V12).
 * source: MODULE | ORG | PLATFORM | DEFAULT.
 */
public record RetentionView(int defaultMonths, Integer platformMonths, Integer organizationMonths, int organizationEffective,
                            int generalMinimum, List<ModuleRetention> modules) {

    public record ModuleRetention(String moduleCode, String nameEs, String nameEn, int legalMinimum, Integer configured,
                                  int effective, String source) {
    }
}
