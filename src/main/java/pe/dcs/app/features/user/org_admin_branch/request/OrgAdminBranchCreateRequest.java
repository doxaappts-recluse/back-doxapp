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

    @NotBlank(message = "{error.elNombreEsObligatorio}")
    private String name;

    @NotBlank(message = "{error.elApellidoEsObligatorio}")
    private String lastname;

    @NotBlank(message = "{error.sexoObligatorio}")
    private String sex;

    @NotBlank(message = "{error.elDniEsObligatorio}")
    private String dni;

    private String phone;

    private String address;

    private LocalDate dateBirth;

    private MaritalStatusType maritalStatus;

    private Integer children;

    // ============================
    // CREDENTIAL
    // ============================

    @NotBlank(message = "{error.usernameObligatorio}")
    private String username;

    @NotBlank(message = "{error.passwordObligatorio}")
    private String password;

    // ============================
    // ACCESS
    // ============================

    @NotNull(message = "{error.laOrganizacionEsObligatoria}")
    private UUID organizationId;

    /**
     * Sede del ACCESO administrativo (alcance de lo que este rol
     * puede gestionar). Obligatoria para ORG_BRANCH_ADMIN/ORG_USER,
     * null para ORG_ADMIN (global, sin sede) — ver
     * OrgAdminBranchServiceImpl.validateBranchRequired(). NO se debe
     * confundir con personBranchId: esto es el ALCANCE del rol, no
     * la sede a la que pertenece la persona como tal.
     */
    private UUID branchId;

    @NotNull(message = "{error.elRolEsObligatorio}")
    private UUID roleId;

    // ============================
    // PERSON BRANCH
    // ============================

    /**
     * Sede a la que pertenece la PERSONA (se usa para crear su
     * PersonBranch), independiente del alcance del acceso que se le
     * otorga. Siempre obligatoria: incluso un ORG_ADMIN (acceso
     * global, sin sede) es una persona real que necesita una sede
     * "de origen" para el resto de módulos que exigen que la persona
     * tenga una sede activa (Membresía, Bautizo, RRHH, etc.). Antes
     * se reutilizaba branchId para esto, así que un ORG_ADMIN
     * quedaba sin ningún PersonBranch (branchId null -> se saltaba
     * su creación) — corregido separando ambos campos.
     */
    @NotNull(message = "{error.sedePersonaEsObligatoria}")
    private UUID personBranchId;

    @NotNull(message = "{error.fechaInicioObligatoria}")
    private LocalDate startDate;

}