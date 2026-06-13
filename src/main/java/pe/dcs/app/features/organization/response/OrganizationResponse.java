package pe.dcs.app.features.organization.response;

import lombok.*;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;


@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrganizationResponse {
    private UUID id;
    private String name;
    private String address;
    private String ruc;
    private Boolean status;
}
