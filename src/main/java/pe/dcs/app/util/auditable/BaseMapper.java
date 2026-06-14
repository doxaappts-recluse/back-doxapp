package pe.dcs.app.util.auditable;

public abstract class BaseMapper {
    protected void mapAudit(
            Auditable entity,
            AuditableResponse response
    ) {

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
