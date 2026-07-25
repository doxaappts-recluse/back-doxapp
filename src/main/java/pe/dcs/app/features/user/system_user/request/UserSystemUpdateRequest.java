package pe.dcs.app.features.user.system_user.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.MaritalStatusType;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class UserSystemUpdateRequest {

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

    private UUID roleId;
}