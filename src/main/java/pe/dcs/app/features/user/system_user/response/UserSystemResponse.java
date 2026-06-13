package pe.dcs.app.features.user.system_user.response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private String sex;
    private String dni;
    private String phone;
    private String address;
    private String dateBirth;
    private String maritalStatus;
    private String children;
    private String dateAdmission;
    private UUID roleId;
    private String roleName;
    private String roleCode;
    private String username;
    private Boolean status;
}
