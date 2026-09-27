package pe.dcs.app.security.authz;

/**
 * Acciones delegables (D10): V ver · C crear · E editar · D eliminar (lógico) · S cambiar estado.
 * Extras por módulo: A aprobar · X exportar · P publicar/anonimizar · H ver sensibles · M fusionar · I importar · T tomar/asignar · O omitir requisitos (con motivo) · Q gestionar verificaciones (screening) · Y anular (finanzas, M15) · K cerrar caja/periodo (finanzas, M15) · Z reabrir periodo (finanzas, M15).
 */
public enum Action {
    V, C, E, D, S, A, X, P, H, M, I, T, O, Q, Y, K, Z
}
