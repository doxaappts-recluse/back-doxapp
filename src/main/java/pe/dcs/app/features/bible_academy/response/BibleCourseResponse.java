package pe.dcs.app.features.bible_academy.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class BibleCourseResponse extends AuditableResponse {

    private UUID id;

    private String name;

    private String description;

    private UUID curriculumId;
    private String curriculumName;
    private Integer order;

    private UUID branchId;
    private String branchName;

    private boolean extra;

    private StatusType status;

    private long classCount;

    private boolean canManage;
}
