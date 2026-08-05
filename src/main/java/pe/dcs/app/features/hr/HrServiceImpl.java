package pe.dcs.app.features.hr;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.FinancialMovement;
import pe.dcs.app.entity.LeaveRequest;
import pe.dcs.app.entity.PayrollRecord;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.StaffMember;
import pe.dcs.app.features.finance.FinancialAccessGuard;
import pe.dcs.app.features.hr.mapper.HrMapper;
import pe.dcs.app.features.hr.request.LeaveRequestDecisionRequest;
import pe.dcs.app.features.hr.request.LeaveRequestFormRequest;
import pe.dcs.app.features.hr.request.LeaveRequestSearchRequest;
import pe.dcs.app.features.hr.request.PayrollRecordFormRequest;
import pe.dcs.app.features.hr.request.PayrollRecordSearchRequest;
import pe.dcs.app.features.hr.request.StaffMemberFormRequest;
import pe.dcs.app.features.hr.request.StaffMemberSearchRequest;
import pe.dcs.app.features.hr.response.HrPersonSearchResponse;
import pe.dcs.app.features.hr.response.LeaveRequestResponse;
import pe.dcs.app.features.hr.response.PayrollRecordResponse;
import pe.dcs.app.features.hr.response.StaffMemberResponse;
import pe.dcs.app.features.hr.service.HrService;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.FinancialMovementRepository;
import pe.dcs.app.repository.LeaveRequestRepository;
import pe.dcs.app.repository.MembershipRepository;
import pe.dcs.app.repository.PayrollRecordRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.repository.StaffMemberRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.finance.FinancialMovementCategory;
import pe.dcs.app.util.enums.finance.FinancialMovementStatus;
import pe.dcs.app.util.enums.finance.FinancialMovementType;
import pe.dcs.app.util.enums.hr.HrApprovalStatus;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * RRHH. Ficha de empleado (StaffMember) por sede, no delegable
 * (mismo criterio que InventoryItem/ReservableSpace). Vacaciones/
 * Permisos (LeaveRequest) y Planilla (PayrollRecord) delegables a la
 * sede — incluyendo aprobar/rechazar permisos. Sin bypass SYSTEM en
 * ningún punto — ver HrAccessGuard.
 */
@Service
@RequiredArgsConstructor
public class HrServiceImpl implements HrService {

    private final StaffMemberRepository staffRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PayrollRecordRepository payrollRecordRepository;
    private final BranchRepository branchRepository;
    private final PersonRepository personRepository;
    private final MembershipRepository membershipRepository;
    private final FinancialMovementRepository financialMovementRepository;
    private final HrMapper mapper;
    private final AuthContext authContext;
    private final HrAccessGuard accessGuard;
    private final FinancialAccessGuard financialAccessGuard;

    // =====================================================
    // BUSCAR PERSONA POR DNI (para crear ficha de empleado)
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public HrPersonSearchResponse findPersonByDni(String dni) {

        accessGuard.assertCanUseStaff();

        UUID organizationId = authContext.getCurrentOrganizationId();

        if (organizationId == null) {
            throw new Exceptions("error.noTieneContextoOrganizacionActivo", HttpStatus.FORBIDDEN);
        }

        if (dni == null || dni.isBlank()) {
            throw new Exceptions("error.elDniEsObligatorio", HttpStatus.BAD_REQUEST);
        }

        Person person =
                personRepository.findByDniInOrganization(dni, organizationId)
                        .orElseThrow(() -> new Exceptions(
                                "error.noEncontroNingunaPersonaDniOrganizacion",
                                HttpStatus.NOT_FOUND
                        ));

        boolean isMember =
                membershipRepository.existsByPersonIdAndCurrentTrueAndStatus(person.getId(), StatusType.ACTIVE);

        return new HrPersonSearchResponse(
                person.getId(),
                person.getName(),
                person.getLastname(),
                person.getDni(),
                isMember
        );
    }

    // =====================================================
    // FICHA DE EMPLEADO
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StaffMemberResponse> searchStaff(StaffMemberSearchRequest request) {

        accessGuard.assertCanUseStaff();

        Pageable pageable = PageableUtil.buildPageable(request.getPagination(), request.getSorts());

        Page<StaffMember> page =
                staffRepository.findAll(
                        StaffMemberSpecification.filter(request.getFilters(), authContext),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(staff -> mapper.toStaffResponse(
                                staff,
                                payrollRecordRepository.countByStaffId(staff.getId()),
                                leaveRequestRepository.countByStaffIdAndStatus(staff.getId(), HrApprovalStatus.PENDING),
                                accessGuard.canManageStaff(staff),
                                showAudit
                        ))
                        .toList(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public StaffMemberResponse getStaffById(UUID id) {

        accessGuard.assertCanUseStaff();

        StaffMember staff = findStaffOrThrow(id);

        return mapper.toStaffResponse(
                staff,
                payrollRecordRepository.countByStaffId(staff.getId()),
                leaveRequestRepository.countByStaffIdAndStatus(staff.getId(), HrApprovalStatus.PENDING),
                accessGuard.canManageStaff(staff),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional
    public UUID createStaff(StaffMemberFormRequest request) {

        accessGuard.assertCanCreateStaff();

        if (request.getPersonId() == null) {
            throw new Exceptions("error.debeSeleccionarPersonaVincular", HttpStatus.BAD_REQUEST);
        }

        if (request.getPosition() == null || request.getPosition().isBlank()) {
            throw new Exceptions("error.elCargoEsObligatorio", HttpStatus.BAD_REQUEST);
        }

        if (request.getContractType() == null) {
            throw new Exceptions("error.debeIndicarTipoContrato", HttpStatus.BAD_REQUEST);
        }

        if (request.getHireDate() == null) {
            throw new Exceptions("error.fechaIngresoObligatoria", HttpStatus.BAD_REQUEST);
        }

        UUID branchId = accessGuard.resolveBranchId(request.getBranchId());

        if (branchId == null) {
            throw new Exceptions("error.debeSeleccionarSedeEmpleado", HttpStatus.BAD_REQUEST);
        }

        StaffMember staff = new StaffMember();
        staff.setPerson(findPersonOrThrow(request.getPersonId()));
        staff.setBranch(findBranchOrThrow(branchId));
        staff.setPosition(request.getPosition());
        staff.setContractType(request.getContractType());
        staff.setBaseSalary(request.getBaseSalary());
        staff.setHireDate(request.getHireDate());
        staff.setTerminationDate(request.getTerminationDate());
        staff.setNotes(request.getNotes());
        staff.setStatus(request.getStatus() != null ? request.getStatus() : StatusType.ACTIVE);

        staffRepository.save(staff);

        return staff.getId();
    }

    @Override
    @Transactional
    public void updateStaff(UUID id, StaffMemberFormRequest request) {

        StaffMember staff = findStaffOrThrow(id);

        accessGuard.assertCanManageStaff(staff);

        if (request.getPosition() == null || request.getPosition().isBlank()) {
            throw new Exceptions("error.elCargoEsObligatorio", HttpStatus.BAD_REQUEST);
        }

        if (request.getContractType() == null) {
            throw new Exceptions("error.debeIndicarTipoContrato", HttpStatus.BAD_REQUEST);
        }

        staff.setPosition(request.getPosition());
        staff.setContractType(request.getContractType());
        staff.setBaseSalary(request.getBaseSalary());

        if (request.getHireDate() != null) {
            staff.setHireDate(request.getHireDate());
        }

        staff.setTerminationDate(request.getTerminationDate());
        staff.setNotes(request.getNotes());

        if (request.getStatus() != null) {
            staff.setStatus(request.getStatus());
        }

        // La persona vinculada y la sede no se reasignan tras crear la
        // ficha — mismo criterio que InventoryItem.branch.

        staffRepository.save(staff);
    }

    // =====================================================
    // VACACIONES / PERMISOS
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LeaveRequestResponse> searchLeaveRequests(LeaveRequestSearchRequest request) {

        accessGuard.assertCanUseLeaveRequest();

        Pageable pageable = PageableUtil.buildPageable(request.getPagination(), request.getSorts());

        Page<LeaveRequest> page =
                leaveRequestRepository.findAll(
                        LeaveRequestSpecification.filter(request.getFilters(), authContext),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(lr -> mapper.toLeaveRequestResponse(lr, accessGuard.canManageLeaveRequest(lr), showAudit))
                        .toList(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public LeaveRequestResponse getLeaveRequestById(UUID id) {

        accessGuard.assertCanUseLeaveRequest();

        LeaveRequest leaveRequest = findLeaveRequestOrThrow(id);

        return mapper.toLeaveRequestResponse(leaveRequest, accessGuard.canManageLeaveRequest(leaveRequest), authContext.canViewAudit());
    }

    @Override
    @Transactional
    public UUID createLeaveRequest(LeaveRequestFormRequest request) {

        accessGuard.assertCanCreateLeaveRequest();

        if (request.getStaffId() == null) {
            throw new Exceptions("error.debeSeleccionarEmpleado", HttpStatus.BAD_REQUEST);
        }

        if (request.getType() == null) {
            throw new Exceptions("error.debeIndicarTipoSolicitud", HttpStatus.BAD_REQUEST);
        }

        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new Exceptions("error.debeIndicarRangoFechas", HttpStatus.BAD_REQUEST);
        }

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new Exceptions("error.fechaFinNoPuedeSerAnterior", HttpStatus.BAD_REQUEST);
        }

        StaffMember staff = findStaffOrThrow(request.getStaffId());

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setStaff(staff);
        leaveRequest.setType(request.getType());
        leaveRequest.setStartDate(request.getStartDate());
        leaveRequest.setEndDate(request.getEndDate());
        leaveRequest.setReason(request.getReason());
        leaveRequest.setStatus(HrApprovalStatus.PENDING);

        leaveRequestRepository.save(leaveRequest);

        return leaveRequest.getId();
    }

    @Override
    @Transactional
    public void approveLeaveRequest(UUID id, LeaveRequestDecisionRequest request) {

        LeaveRequest leaveRequest = findLeaveRequestOrThrow(id);

        accessGuard.assertCanManageLeaveRequest(leaveRequest);

        assertPending(leaveRequest);

        leaveRequest.setStatus(HrApprovalStatus.APPROVED);
        leaveRequest.setApprovedByUser(findPersonOrThrow(authContext.getUserId()));
        leaveRequest.setApprovedAt(Instant.now());
        leaveRequest.setObservations(request != null ? request.getObservations() : null);

        leaveRequestRepository.save(leaveRequest);
    }

    @Override
    @Transactional
    public void rejectLeaveRequest(UUID id, LeaveRequestDecisionRequest request) {

        LeaveRequest leaveRequest = findLeaveRequestOrThrow(id);

        accessGuard.assertCanManageLeaveRequest(leaveRequest);

        assertPending(leaveRequest);

        if (request == null || request.getObservations() == null || request.getObservations().isBlank()) {
            throw new Exceptions("error.debeIndicarMotivoRechazo", HttpStatus.BAD_REQUEST);
        }

        leaveRequest.setStatus(HrApprovalStatus.REJECTED);
        leaveRequest.setApprovedByUser(findPersonOrThrow(authContext.getUserId()));
        leaveRequest.setApprovedAt(Instant.now());
        leaveRequest.setObservations(request.getObservations());

        leaveRequestRepository.save(leaveRequest);
    }

    private void assertPending(LeaveRequest leaveRequest) {
        if (leaveRequest.getStatus() != HrApprovalStatus.PENDING) {
            throw new Exceptions("error.solicitudFueResuelta", HttpStatus.BAD_REQUEST);
        }
    }

    // =====================================================
    // PLANILLA
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PayrollRecordResponse> searchPayrollRecords(PayrollRecordSearchRequest request) {

        accessGuard.assertCanUsePayroll();

        Pageable pageable = PageableUtil.buildPageable(request.getPagination(), request.getSorts());

        Page<PayrollRecord> page =
                payrollRecordRepository.findAll(
                        PayrollRecordSpecification.filter(request.getFilters(), authContext),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(p -> mapper.toPayrollRecordResponse(p, accessGuard.canManagePayroll(p), showAudit))
                        .toList(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PayrollRecordResponse getPayrollRecordById(UUID id) {

        accessGuard.assertCanUsePayroll();

        PayrollRecord payroll = findPayrollOrThrow(id);

        return mapper.toPayrollRecordResponse(payroll, accessGuard.canManagePayroll(payroll), authContext.canViewAudit());
    }

    /**
     * Los pagos de planilla NO se editan ni eliminan tras crearse —
     * mismo criterio que InventoryMovement (libro de movimientos): una
     * corrección se maneja con un ajuste manual en Finanzas.
     */
    @Override
    @Transactional
    public UUID createPayrollRecord(PayrollRecordFormRequest request) {

        accessGuard.assertCanCreatePayroll();

        if (request.getStaffId() == null) {
            throw new Exceptions("error.debeSeleccionarEmpleado", HttpStatus.BAD_REQUEST);
        }

        if (request.getPeriodMonth() == null || request.getPeriodMonth() < 1 || request.getPeriodMonth() > 12) {
            throw new Exceptions("error.debeIndicarMesValido112", HttpStatus.BAD_REQUEST);
        }

        if (request.getPeriodYear() == null) {
            throw new Exceptions("error.debeIndicarAnoPeriodo", HttpStatus.BAD_REQUEST);
        }

        StaffMember staff = findStaffOrThrow(request.getStaffId());

        BigDecimal baseSalary = request.getBaseSalary() != null ? request.getBaseSalary() : staff.getBaseSalary();

        if (baseSalary == null) {
            throw new Exceptions(
                    "error.empleadoNoTieneSalarioBaseDefinido",
                    HttpStatus.BAD_REQUEST
            );
        }

        BigDecimal bonuses = request.getBonuses() != null ? request.getBonuses() : BigDecimal.ZERO;
        BigDecimal deductions = request.getDeductions() != null ? request.getDeductions() : BigDecimal.ZERO;
        BigDecimal netAmount = baseSalary.add(bonuses).subtract(deductions);

        if (netAmount.signum() < 0) {
            throw new Exceptions("error.montoNetoNoPuedeSerNegativo", HttpStatus.BAD_REQUEST);
        }

        LocalDate paymentDate = request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now();

        PayrollRecord payroll = new PayrollRecord();
        payroll.setStaff(staff);
        payroll.setPeriodMonth(request.getPeriodMonth());
        payroll.setPeriodYear(request.getPeriodYear());
        payroll.setBaseSalary(baseSalary);
        payroll.setBonuses(bonuses);
        payroll.setDeductions(deductions);
        payroll.setNetAmount(netAmount);
        payroll.setPaymentDate(paymentDate);
        payroll.setPaymentMethod(request.getPaymentMethod());
        payroll.setNotes(request.getNotes());

        payrollRecordRepository.save(payroll);

        syncFinancialMovement(payroll, staff);

        return payroll.getId();
    }

    /**
     * A diferencia de InventoryMovement (que solo crea el
     * FinancialMovement condicionalmente), el pago de planilla
     * SIEMPRE genera uno vinculado (categoría PAYROLL, tipo EXPENSE) —
     * mismo criterio incondicional que MarriageServiceImpl con la
     * tarifa de matrimonio.
     */
    private void syncFinancialMovement(PayrollRecord payroll, StaffMember staff) {

        Branch branch = staff.getBranch();

        FinancialMovement financialMovement = new FinancialMovement();
        financialMovement.setOrganization(branch.getOrganization());
        financialMovement.setBranch(branch);
        financialMovement.setCategory(FinancialMovementCategory.PAYROLL);
        financialMovement.setType(FinancialMovementType.EXPENSE);
        financialMovement.setPaymentMethod(payroll.getPaymentMethod());

        String staffName =
                staff.getPerson() != null
                        ? staff.getPerson().getName() + " " + staff.getPerson().getLastname()
                        : "empleado";

        financialMovement.setConcept(
                "Pago de planilla: " + staffName + " - " + payroll.getPeriodMonth() + "/" + payroll.getPeriodYear()
        );
        financialMovement.setAmount(payroll.getNetAmount());
        financialMovement.setMovementDate(payroll.getPaymentDate());
        financialMovement.setCreatedByUser(findPersonOrThrow(authContext.getUserId()));

        if (financialAccessGuard.canApprove(branch)) {

            financialMovement.setStatus(FinancialMovementStatus.APPROVED);
            financialMovement.setApprovedByUser(financialMovement.getCreatedByUser());
            financialMovement.setApprovedAt(Instant.now());

        } else {

            financialMovement.setStatus(FinancialMovementStatus.PENDING);
        }

        financialMovementRepository.save(financialMovement);

        payroll.setFinancialMovement(financialMovement);
        payrollRecordRepository.save(payroll);
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private StaffMember findStaffOrThrow(UUID id) {
        return staffRepository.findById(id)
                .orElseThrow(() -> new Exceptions("error.fichaEmpleadoNoEncontrada", HttpStatus.NOT_FOUND));
    }

    private LeaveRequest findLeaveRequestOrThrow(UUID id) {
        return leaveRequestRepository.findById(id)
                .orElseThrow(() -> new Exceptions("error.solicitudNoEncontrada", HttpStatus.NOT_FOUND));
    }

    private PayrollRecord findPayrollOrThrow(UUID id) {
        return payrollRecordRepository.findById(id)
                .orElseThrow(() -> new Exceptions("error.pagoPlanillaNoEncontrado", HttpStatus.NOT_FOUND));
    }

    private Branch findBranchOrThrow(UUID id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new Exceptions("error.sedeNoEncontrada2", HttpStatus.NOT_FOUND));
    }

    private Person findPersonOrThrow(UUID id) {
        return personRepository.findById(id)
                .orElseThrow(() -> new Exceptions("error.personaNoEncontrada", HttpStatus.NOT_FOUND));
    }
}
