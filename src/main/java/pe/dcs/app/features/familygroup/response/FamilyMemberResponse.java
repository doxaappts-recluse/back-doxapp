package pe.dcs.app.features.familygroup.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.FamilyRole;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class FamilyMemberResponse {

    private UUID id;

    private UUID personId;
    private String name;
    private String dni;
    private boolean member;

    private FamilyRole role;

    private LocalDate joinDate;
}
