package pe.dcs.app.features.organization.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class OrganizationCreateRequest {
    private String name;
    private String address;
    private String ruc;
    private String email;

    @NotNull(message = "La fecha de fundación es obligatoria")
    private LocalDate foundedDate;
}
