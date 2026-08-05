package pe.dcs.app.features.bible_academy.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class BibleCourseFormRequest {

    @NotBlank(message = "{error.nombreCursoObligatorio}")
    private String name;

    private String description;

    /**
     * Exactamente uno de los dos debe venir informado (ver
     * BibleAcademyServiceImpl.validateCourseForm):
     *
     * - curriculumId + order: nivel de malla (solo org admin).
     * - branchId: curso extra de esa sede (org admin elige libre,
     *   cualquier otro rol usa su propia sede — ver
     *   BibleAcademyAccessGuard.resolveBranchId).
     */
    private UUID curriculumId;
    private Integer order;

    private UUID branchId;

    private StatusType status;
}
