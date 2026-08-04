package pe.dcs.app.features.bible_academy.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.bible_academy.BibleCurriculumStatus;

@Getter
@Setter
public class BibleCurriculumFilterRequest {

    private String name;

    private BibleCurriculumStatus status;
}
