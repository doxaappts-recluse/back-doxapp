package pe.dcs.app.features.module.dto;

import java.util.List;

/** {@code activeContracts} y {@code plans}: uso actual del módulo (para decidir si se puede retirar). */
public record ModuleResponse(
        String code, String nameEs, String nameEn, List<String> levels, String parentCode, String kind,
        List<String> actions, boolean delegable, String route, String icon, int sortOrder, String status,
        long activeContracts, long plans, List<String> allowedStatuses
) {
}
