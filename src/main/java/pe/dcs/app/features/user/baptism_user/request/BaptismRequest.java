package pe.dcs.app.features.user.baptism_user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class BaptismRequest {

    @NotNull
    private UUID userId;

    @NotNull
    private LocalDate baptismDate;

    @NotBlank
    private String churchName;

    private String pastorName;

    private String city;

    private Boolean verified;

    private String observations;

}