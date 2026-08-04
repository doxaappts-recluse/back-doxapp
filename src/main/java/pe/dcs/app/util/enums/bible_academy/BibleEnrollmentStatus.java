package pe.dcs.app.util.enums.bible_academy;

/**
 * Estado de una matrícula (BibleEnrollment). Solo APPROVED habilita
 * el prerequisito del siguiente nivel de una malla y el certificado
 * de finalización.
 */
public enum BibleEnrollmentStatus {
    ENROLLED,
    APPROVED,
    FAILED,
    WITHDRAWN
}
