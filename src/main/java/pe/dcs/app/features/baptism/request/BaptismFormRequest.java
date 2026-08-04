package pe.dcs.app.features.baptism.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class BaptismFormRequest {

    private LocalDate baptismDate;

    private String churchName;

    private String pastorName;

    private String city;

    private boolean verified;

    private String observations;
}
