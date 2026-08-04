package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pe.dcs.app.entity.InventoryMovement;

import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID>, JpaSpecificationExecutor<InventoryMovement> {

    long countByItemId(UUID itemId);
}
