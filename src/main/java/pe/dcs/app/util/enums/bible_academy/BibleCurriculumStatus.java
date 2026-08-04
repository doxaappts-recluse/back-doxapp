package pe.dcs.app.util.enums.bible_academy;

/**
 * Ciclo de vida de una malla curricular (BibleCurriculum). Solo puede
 * haber UNA malla ACTIVA por organización a la vez (ver
 * BibleAcademyServiceImpl.activateCurriculum) — las iglesias usan una
 * sola malla oficial para su Academia Bíblica, sin tracks paralelos.
 *
 * DRAFT   : el org admin todavía está armando los niveles/cursos, no
 *           habilita dictados en ninguna sede.
 * ACTIVE  : la malla vigente — las sedes solo pueden abrir dictados
 *           (BibleClass) de cursos que cuelguen de una malla ACTIVE.
 * RETIRED : reemplazada por una malla nueva (o retirada manualmente).
 *           Los dictados/matrículas que ya existían quedan intactos
 *           como historial, pero no se pueden abrir dictados nuevos.
 */
public enum BibleCurriculumStatus {
    DRAFT,
    ACTIVE,
    RETIRED
}
