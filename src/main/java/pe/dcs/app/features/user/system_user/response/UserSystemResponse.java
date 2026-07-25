package pe.dcs.app.features.user.system_user.response;

import lombok.*;
import pe.dcs.app.util.enums.MaritalStatusType;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.RoleType;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSystemResponse {

    private UUID id;
    private String name;
    private String lastname;
    private String dni;
    private String sex;
    private String phone;
    private String address;
    private LocalDate dateBirth;
    private MaritalStatusType maritalStatus;
    private Integer children;
    private LocalDate dateAdmission;

    private String username;

    private UUID roleId;
    private String roleName;
    private RoleType roleCode;
    private StatusType status;
}
