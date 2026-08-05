package pe.dcs.app.features.bible_academy.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BibleCurriculumFormRequest {

    @NotBlank(message = "{error.nombreMallaObligatorio}")
    private String name;

    private String description;
}
