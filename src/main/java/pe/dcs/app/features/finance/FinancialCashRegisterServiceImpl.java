package pe.dcs.app.features.finance;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.FinancialCashRegister;
import pe.dcs.app.entity.FinancialMovement;
import pe.dcs.app.entity.Person;
import pe.dcs.app.features.finance.request.FinancialCashRegisterCloseRequest;
import pe.dcs.app.features.finance.request.FinancialCashRegisterOpenRequest;
import pe.dcs.app.features.finance.response.FinancialCashRegisterResponse;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.FinancialCashRegisterRepository;
import pe.dcs.app.repository.FinancialMovementRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.finance.FinancialCashRegisterStatus;
import pe.dcs.app.util.enums.finance.FinancialMovementPaymentMethod;
import pe.dcs.app.util.enums.finance.FinancialMovementStatus;
import pe.dcs.app.util.enums.finance.FinancialMovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinancialCashRegisterServiceImpl implements FinancialCashRegisterService {

    private final FinancialCashRegisterRepository financialCashRegisterRepository;
    private final FinancialMovementRepository financialMovementRepository;
    private final BranchRepository branchRepository;
    private final PersonRepository personRepository;
    private final AuthContext authContext;
    private final FinancialCashRegisterAccessGuard accessGuard;
    private final FinancialCashRegisterMapper financialCashRegisterMapper;

    private Branch findBranch(UUID branchId) {

        return branchRepository.findById(branchId)
                .orElseThrow(() ->
                        new Exceptions(
                                "error.sedeNoEncontrada",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    private Person currentUser() {

        UUID userId = authContext.getUserId();

        if (userId == null) {
            throw new Exceptions(
                    "error.noPudoDeterminarUsuarioActual",
                    HttpStatus.BAD_REQUEST
            );
        }

        return personRepository.findById(userId)
                .orElseThrow(() ->
                        new Exceptions(
                                "error.usuarioNoEncontrado",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    private FinancialCashRegister findById(UUID id) {

        FinancialCashRegister register =
                financialCashRegisterRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.cajaNoEncontrada",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        accessGuard.assertSameOrganization(register.getBranch());

        return register;
    }

    @Override
    @Transactional
    public FinancialCashRegisterResponse open(FinancialCashRegisterOpenRequest request) {

        if (request.getBranchId() == null) {
            throw new Exceptions(
                    "error.debeIndicarSede",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.getOpeningBalance() == null) {
            throw new Exceptions(
                    "error.debeIndicarMontoApertura",
                    HttpStatus.BAD_REQUEST
            );
        }

        Branch branch = findBranch(request.getBranchId());

        accessGuard.assertSameOrganization(branch);
        accessGuard.assertCanOpen(branch);

        LocalDate registerDate =
                request.getRegisterDate() != null
                        ? request.getRegisterDate()
                        : LocalDate.now();

        if (financialCashRegisterRepository.existsByBranchIdAndStatus(
                branch.getId(),
                FinancialCashRegisterStatus.OPEN
        )) {
            throw new Exceptions(
                    "error.existeCajaAbiertaSedeDebeCerrarla",
                    HttpStatus.BAD_REQUEST
            );
        }

        FinancialCashRegister register = new FinancialCashRegister();

        register.setOrganization(branch.getOrganization());
        register.setBranch(branch);
        register.setRegisterDate(registerDate);
        register.setOpeningBalance(request.getOpeningBalance());
        register.setNotes(request.getNotes());
        register.setStatus(FinancialCashRegisterStatus.OPEN);
        register.setOpenedByUser(currentUser());
        register.setOpenedAt(Instant.now());

        return financialCashRegisterMapper.simple(
                financialCashRegisterRepository.save(register),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional
    public FinancialCashRegisterResponse close(UUID id, FinancialCashRegisterCloseRequest request) {

        if (request.getClosingBalance() == null) {
            throw new Exceptions(
                    "error.debeIndicarMontoCierre",
                    HttpStatus.BAD_REQUEST
            );
        }

        FinancialCashRegister register = findById(id);

        accessGuard.assertCanClose(register);

        if (register.getStatus() != FinancialCashRegisterStatus.OPEN) {
            throw new Exceptions(
                    "error.estaCajaYaEstaCerrada",
                    HttpStatus.BAD_REQUEST
            );
        }

        BigDecimal expectedBalance = computeExpectedBalance(register);

        register.setExpectedBalance(expectedBalance);
        register.setClosingBalance(request.getClosingBalance());
        register.setDifference(request.getClosingBalance().subtract(expectedBalance));

        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            register.setNotes(request.getNotes());
        }

        register.setStatus(FinancialCashRegisterStatus.CLOSED);
        register.setClosedByUser(currentUser());
        register.setClosedAt(Instant.now());

        return financialCashRegisterMapper.simple(
                financialCashRegisterRepository.save(register),
                authContext.canViewAudit()
        );
    }

    /**
     * Saldo esperado = apertura + movimientos APROBADOS en efectivo
     * (CASH) del día de la caja en esa sede — ingresos suman, gastos
     * restan. FinancialMovementSpecification no tiene filtro por
     * paymentMethod, así que se trae el universo del día por sede/
     * estado y se filtra por efectivo en memoria (el volumen diario
     * de una sola sede es chico, no vale la pena tocar la
     * Specification compartida por esto).
     */
    private BigDecimal computeExpectedBalance(FinancialCashRegister register) {

        Specification<FinancialMovement> spec =
                FinancialMovementSpecification.filter(
                        authContext,
                        register.getBranch().getId(),
                        null,
                        null,
                        FinancialMovementStatus.APPROVED,
                        null,
                        null,
                        register.getRegisterDate(),
                        register.getRegisterDate(),
                        null
                );

        List<FinancialMovement> movements = financialMovementRepository.findAll(spec);

        BigDecimal net = movements.stream()
                .filter(m -> m.getPaymentMethod() == FinancialMovementPaymentMethod.CASH)
                .map(m -> m.getType() == FinancialMovementType.EXPENSE
                        ? m.getAmount().negate()
                        : m.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return register.getOpeningBalance().add(net);
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialCashRegisterResponse getById(UUID id) {

        return financialCashRegisterMapper.simple(
                findById(id),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<FinancialCashRegisterResponse> listAll() {

        boolean showAudit = authContext.canViewAudit();

        List<FinancialCashRegister> registers;

        if (authContext.isSystem() && authContext.getCurrentOrganizationId() == null) {

            registers = financialCashRegisterRepository.findAll();

        } else if (authContext.isSystem() || authContext.isCurrentOrganizationAdmin()) {

            UUID organizationId = authContext.getCurrentOrganizationId();

            if (organizationId == null) {
                throw new Exceptions(
                        "error.noPudoDeterminarOrganizacionActual",
                        HttpStatus.BAD_REQUEST
                );
            }

            registers =
                    financialCashRegisterRepository
                            .findByOrganizationIdOrderByRegisterDateDescCreatedAtDesc(organizationId);

        } else {

            UUID branchId = authContext.getCurrentBranchId();

            if (branchId == null) {
                throw new Exceptions(
                        "error.noPudoDeterminarSedeActual",
                        HttpStatus.BAD_REQUEST
                );
            }

            registers =
                    financialCashRegisterRepository
                            .findByBranchIdOrderByRegisterDateDescCreatedAtDesc(branchId);
        }

        return registers.stream()
                .map(r -> financialCashRegisterMapper.simple(r, showAudit))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialCashRegisterResponse getOpenByBranch(UUID branchId) {

        Branch branch = findBranch(branchId);

        accessGuard.assertSameOrganization(branch);

        return financialCashRegisterRepository
                .findByBranchIdAndStatus(branchId, FinancialCashRegisterStatus.OPEN)
                .map(r -> financialCashRegisterMapper.simple(r, authContext.canViewAudit()))
                .orElse(null);
    }
}
