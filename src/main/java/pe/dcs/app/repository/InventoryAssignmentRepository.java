package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pe.dcs.app.entity.InventoryAssignment;

import java.util.UUID;

public interface InventoryAssignmentRepository extends JpaRepository<InventoryAssignment, UUID>, JpaSpecificationExecutor<InventoryAssignment> {

    long countByItemIdAndReturnedDateIsNull(UUID itemId);
}
