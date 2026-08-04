package pe.dcs.app.features.marriage.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class MarriageFilterRequest {

    /**
     * Busca por nombre de cualquiera de los dos cónyuges (contains,
     * case-insensitive) — ver MarriageSpecification.
     */
    private String spouseName;

    private String churchName;

    private LocalDate startDate;

    private LocalDate endDate;

    /**
     * Solo relevante para org admin/SYSTEM; para branch admin/org
     * user delegado el scope ya lo fija MarriageSpecification con
     * la sede actual.
     */
    private UUID branchId;
}
