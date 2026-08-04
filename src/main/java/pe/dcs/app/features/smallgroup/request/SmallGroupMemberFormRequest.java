package pe.dcs.app.features.smallgroup.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class SmallGroupMemberFormRequest {

    /**
     * Setear solo si el participante se encontró por DNI. Si es
     * null, se guarda solo con guestName/guestPhone — no se crea una
     * Person nueva. Los grupos pequeños no son exclusivos de
     * miembros.
     */
    private UUID personId;

    private String guestName;

    private String guestPhone;

    private LocalDate joinDate;
}
