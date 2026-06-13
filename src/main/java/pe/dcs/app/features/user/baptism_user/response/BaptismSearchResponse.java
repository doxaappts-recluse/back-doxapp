package pe.dcs.app.features.user.baptism_user.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.features.user.shared.BaseUserSearchResponse;

import java.time.LocalDate;

@Getter
@Setter
public class BaptismSearchResponse {

    private BaseUserSearchResponse user;

    private Boolean hasBaptism;

    private LocalDate baptismDate;

    private String churchName;

    private Boolean verified;

}