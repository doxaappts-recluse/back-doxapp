package pe.dcs.app.features.bible_academy.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.bible_academy.BibleEnrollmentStatus;

@Getter
@Setter
public class BibleEnrollmentFilterRequest {

    private String personName;

    private BibleEnrollmentStatus status;
}
