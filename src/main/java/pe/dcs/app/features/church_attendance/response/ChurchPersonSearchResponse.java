package pe.dcs.app.features.church_attendance.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Resultado de buscar una Person por DNI para marcarla presente en un
 * culto — mismo patrón que MarriageSpouseSearchResponse/
 * SmallGroupPersonSearchResponse.
 */
@Getter
@Setter
@AllArgsConstructor
public class ChurchPersonSearchResponse {

    private UUID personId;
    private String name;
    private String lastname;
    private String dni;
    private boolean member;
}
