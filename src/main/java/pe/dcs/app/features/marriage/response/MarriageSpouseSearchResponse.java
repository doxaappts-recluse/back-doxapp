package pe.dcs.app.features.marriage.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Resultado de buscar un cónyuge por DNI (ver
 * MarriageServiceImpl.findSpouseByDni). isMember indica si tiene
 * una membresía activa — solo en ese caso el front debe avisar que
 * se actualizará el estado civil al guardar (ver
 * MarriageServiceImpl.syncMaritalStatus).
 */
@Getter
@Setter
@AllArgsConstructor
public class MarriageSpouseSearchResponse {

    private UUID personId;

    private String name;

    private String lastname;

    private String dni;

    private boolean isMember;
}
