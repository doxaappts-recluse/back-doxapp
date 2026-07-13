package pe.dcs.app.features.user.org_user.response;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgAdminResponse {
    private UUID id;
    private String name;
    private String lastname;
    private String dni;
    private String sex;
    private String phone;
    private String address;
    private String dateBirth;
    private String maritalStatus;
    private String children;
    private String dateAdmission;
    private String username;
    private Boolean status;

    // Organización
    private UUID organizationId;
    private String organizationName;

    // Sede

    private UUID branchId;
    private String branchName;
    private Boolean branchMain;

}