package pe.dcs.app.features.user.baptism_user.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class BaptismData {

    private UUID id;

    private LocalDate baptismDate;

    private String churchName;

    private String pastorName;

    private String city;

    private Boolean verified;

    private String observations;

}