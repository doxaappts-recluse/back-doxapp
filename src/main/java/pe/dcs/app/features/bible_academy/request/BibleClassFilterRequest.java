package pe.dcs.app.features.bible_academy.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.bible_academy.BibleClassStatus;

import java.util.UUID;

@Getter
@Setter
public class BibleClassFilterRequest {

    private String courseName;

    private UUID courseId;

    /** Solo relevante para org admin/SYSTEM; el resto ya queda acotado a su sede por BibleClassSpecification. */
    private UUID branchId;

    private BibleClassStatus status;
}
