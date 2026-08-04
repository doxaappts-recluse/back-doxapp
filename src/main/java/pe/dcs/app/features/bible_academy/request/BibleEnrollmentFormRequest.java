package pe.dcs.app.features.bible_academy.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class BibleEnrollmentFormRequest {

    private UUID personId;

    /** Si viene null se usa la fecha de hoy. */
    private LocalDate enrollDate;

    /**
     * Saltarse el prerequisito de nivel de malla: solo lo puede
     * pedir un admin (ver BibleAcademyAccessGuard.assertCanOverridePrerequisite),
     * con motivo obligatorio. Cubre migración de malla vieja a
     * nueva y cualquier otro caso similar (alumno formado en otra
     * iglesia, etc.) sin una pantalla de equivalencias dedicada.
     */
    private boolean overridePrerequisite;
    private String overrideReason;
}
