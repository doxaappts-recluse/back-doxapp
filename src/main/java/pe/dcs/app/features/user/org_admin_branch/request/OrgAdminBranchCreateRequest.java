package pe.dcs.app.features.user.org_admin_branch.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.MaritalStatusType;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class OrgAdminBranchCreateRequest {

    // ============================
    // PERSON
    // ============================

    @NotBlank
    private String name;

    @NotBlank
    private String lastname;

    @NotBlank
    private String sex;

    @NotBlank
    private String dni;

    private String phone;

    private String address;

    private LocalDate dateBirth;

    private MaritalStatusType maritalStatus;

    private Integer children;

    // ============================
    // CREDENTIAL
    // ============================

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    // ============================
    // ACCESS
    // ============================

    @NotNull
    private UUID organizationId;

    private UUID branchId;

    @NotNull
    private UUID roleId;

    // ============================
    // PERSON BRANCH
    // ============================

    @NotNull
    private LocalDate startDate;

}