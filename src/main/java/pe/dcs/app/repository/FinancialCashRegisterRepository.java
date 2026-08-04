package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.FinancialCashRegister;
import pe.dcs.app.util.enums.finance.FinancialCashRegisterStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinancialCashRegisterRepository extends JpaRepository<FinancialCashRegister, UUID> {

    List<FinancialCashRegister> findByOrganizationIdOrderByRegisterDateDescCreatedAtDesc(
            UUID organizationId
    );

    List<FinancialCashRegister> findByBranchIdOrderByRegisterDateDescCreatedAtDesc(
            UUID branchId
    );

    /**
     * La caja OPEN de una sede, si existe — para operar sobre ella
     * (cerrar) sin necesidad de conocer su id, y para validar la
     * regla de "una sola caja OPEN por sede a la vez" antes de abrir
     * una nueva (ver FinancialCashRegisterServiceImpl.open()).
     */
    Optional<FinancialCashRegister> findByBranchIdAndStatus(
            UUID branchId,
            FinancialCashRegisterStatus status
    );

    boolean existsByBranchIdAndStatus(
            UUID branchId,
            FinancialCashRegisterStatus status
    );
}
