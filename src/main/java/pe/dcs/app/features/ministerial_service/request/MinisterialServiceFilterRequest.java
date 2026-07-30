package pe.dcs.app.features.ministerial_service.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MinisterialServiceFilterRequest {

    private String name;

    private String lastname;

    /**
     * true = solo personas que sirven actualmente en algún
     * ministerio (al menos un MinistryAssignment con endDate
     * null). false = solo personas que no. null = sin filtrar.
     */
    private Boolean hasMinistry;

}
