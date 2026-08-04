package pe.dcs.app.features.baptism.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class BaptismDetailResponse {

    private UUID id;

    private LocalDate baptismDate;

    private String churchName;

    private String pastorName;

    private String city;

    private boolean verified;

    private String observations;
}
