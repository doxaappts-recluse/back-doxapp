package pe.dcs.app.features.bible_academy.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.bible_academy.BibleCurriculumStatus;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class BibleCurriculumDetailResponse {

    private UUID id;

    private String name;

    private String description;

    private BibleCurriculumStatus status;

    private boolean canManage;

    /** Niveles ordenados por BibleCourse.order. */
    private List<BibleCourseResponse> courses;
}
