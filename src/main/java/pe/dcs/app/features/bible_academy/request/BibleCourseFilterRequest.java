package pe.dcs.app.features.bible_academy.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class BibleCourseFilterRequest {

    private String name;

    private UUID curriculumId;

    /** Solo relevante para org admin/SYSTEM; el resto ya queda acotado a su sede por BibleCourseSpecification. */
    private UUID branchId;

    /** true = solo cursos extra, false = solo cursos de malla, null = ambos. */
    private Boolean onlyExtra;

    private StatusType status;
}
