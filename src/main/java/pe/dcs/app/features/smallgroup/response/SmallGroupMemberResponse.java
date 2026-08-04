package pe.dcs.app.features.smallgroup.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class SmallGroupMemberResponse {

    private UUID id;

    private UUID personId;
    private String name;
    private String dni;
    private boolean isMember;

    private String guestName;
    private String guestPhone;

    private LocalDate joinDate;

    private StatusType status;
}
