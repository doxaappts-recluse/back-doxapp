package pe.dcs.app.util.auditable;

/**
 * Reusable para llenar los campos de auditoría (createdAt/
 * updatedAt/createdBy/updatedBy) de cualquier response que
 * extienda {@link AuditableResponse}, a partir de cualquier
 * entidad que extienda {@link Auditable}.
 *
 * "visible" lo decide quien llama (normalmente
 * authContext.canViewAudit()): SYSTEM, ORG_ADMIN y
 * ORG_BRANCH_ADMIN ven esta info, un ORG_USER normal no
 * (si visible=false, los campos quedan sin setear/null).
 *
 * Es static para poder usarse tanto desde mappers @Component
 * como desde mappers utilitarios estáticos, sin obligar a
 * heredar de esta clase.
 */
public abstract class BaseMapper {

    public static void mapAudit(
            Auditable entity,
            AuditableResponse response,
            boolean visible
    ) {

        if (!visible || entity == null) {
            return;
        }

        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());

        if (entity.getCreatedBy() != null) {

            response.setCreatedById(
                    entity.getCreatedBy().getId()
            );

            response.setCreatedBy(
                    entity.getCreatedBy().getName()
                            + " "
                            + entity.getCreatedBy().getLastname()
            );
        }

        if (entity.getUpdatedBy() != null) {

            response.setUpdatedById(
                    entity.getUpdatedBy().getId()
            );

            response.setUpdatedBy(
                    entity.getUpdatedBy().getName()
                            + " "
                            + entity.getUpdatedBy().getLastname()
            );
        }
    }
}
