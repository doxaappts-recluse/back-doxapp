package pe.dcs.app.features.bible_academy.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.bible_academy.BibleCurriculumStatus;

import java.util.UUID;

@Getter
@Setter
public class BibleCurriculumSearchRowResponse extends AuditableResponse {

    private UUID id;

    private String name;

    private String description;

    private BibleCurriculumStatus status;

    private long courseCount;

    private boolean canManage;
}
