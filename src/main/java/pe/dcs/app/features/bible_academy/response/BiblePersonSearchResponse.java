package pe.dcs.app.features.bible_academy.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Resultado de buscar una persona por DNI para asignarla como
 * maestro de un dictado o matricularla a uno — mismo patrón que
 * SmallGroupPersonSearchResponse. isMember es solo informativo, la
 * Academia Bíblica no es exclusiva de miembros.
 */
@Getter
@Setter
@AllArgsConstructor
public class BiblePersonSearchResponse {

    private UUID personId;

    private String name;

    private String lastname;

    private String dni;

    private boolean isMember;
}
