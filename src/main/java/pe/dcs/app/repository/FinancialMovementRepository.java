package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.FinancialMovement;

import java.util.UUID;

@Repository
public interface FinancialMovementRepository
        extends JpaRepository<FinancialMovement, UUID>, JpaSpecificationExecutor<FinancialMovement> {
}
