package pe.dcs.app.features.organization.response;

import lombok.*;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.StatusType;

import java.time.LocalDate;
import java.util.UUID;


@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrganizationResponse extends AuditableResponse {
    private UUID id;
    private String name;
    private String address;
    private String ruc;
    private String email;
    private LocalDate foundedDate;
    private Boolean status;
}
