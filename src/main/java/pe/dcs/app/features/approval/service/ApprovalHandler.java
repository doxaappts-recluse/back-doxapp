package pe.dcs.app.features.approval.service;

import java.util.Map;
import java.util.UUID;

/**
 * Núcleo 01 §3 · cada módulo registra un handler para su tipo de solicitud; el motor no conoce las reglas del módulo.
 * Los métodos {@code on*} corren dentro de la transacción de la decisión: si lanzan, la decisión no se aplica.
 */
public interface ApprovalHandler {

    /** Código del tipo de solicitud (BRANCH_TRANSFER, VISIBILITY_ACCESS…). */
    String type();

    /** Módulo dueño: decidir exige su acción A (delegación, contrato y alcance de sede incluidos). */
    String moduleCode();

    /** Prefijo de los avisos: PREFIJO_REQUESTED / _APPROVED / _REJECTED / _EXPIRED (tipos de NotificationType). */
    default String notificationPrefix() {
        return type();
    }

    /** Días de vigencia de la solicitud pendiente; null = no vence. */
    default Integer expiryDays() {
        return null;
    }

    /** false = quien no ve la sede que decide no conoce el nombre de la persona del asunto (p. ej. pedir acceso a datos ajenos). */
    default boolean revealSubject() {
        return true;
    }

    /** Parámetros del aviso para los textos del tipo. */
    default Map<String, String> notifyParams(ApprovalRow request) {
        return Map.of();
    }

    void onApprove(Decision decision);

    default void onReject(Decision decision) {
    }

    default void onCancel(Decision decision) {
    }

    default void onExpire(Decision decision) {
    }

    /** Solicitud tal como está guardada. */
    record ApprovalRow(UUID id, UUID organizationId, UUID branchId, UUID relatedBranchId, String type, String subjectType, UUID subjectId,
                       UUID requestedBy, String status, String reason, Map<String, Object> payload) {
    }

    /** actorPersonId es null cuando decide el sistema (vencimiento). */
    record Decision(ApprovalRow request, UUID actorPersonId, String note) {
    }
}
