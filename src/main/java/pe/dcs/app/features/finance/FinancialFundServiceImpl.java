package pe.dcs.app.features.finance;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.FinancialFund;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.features.finance.request.FinancialFundRequest;
import pe.dcs.app.features.finance.response.FinancialFundResponse;
import pe.dcs.app.repository.FinancialFundRepository;
import pe.dcs.app.repository.OrganizationRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinancialFundServiceImpl implements FinancialFundService {

    private final FinancialFundRepository financialFundRepository;
    private final OrganizationRepository organizationRepository;
    private final AuthContext authContext;
    private final FinancialFundMapper financialFundMapper;

    /**
     * Definir el catálogo de fondos es una decisión administrativa
     * de la organización, no una tarea delegable de registro de
     * datos — solo org admin (o SYSTEM) puede crear/editar/
     * habilitar/inhabilitar. Un branch admin u org user delegado
     * puede LISTARLOS (para elegir uno al registrar un movimiento)
     * pero no gestionarlos.
     */
    private void assertCanManage() {

        if (authContext.isSystem()
                || authContext.isCurrentOrganizationAdmin()) {
            return;
        }

        throw new Exceptions(
                "error.soloAdministradorOrganizacionPuedeGestionarFondos",
                HttpStatus.FORBIDDEN
        );
    }

    private UUID currentOrganizationId() {

        UUID organizationId = authContext.getCurrentOrganizationId();

        if (organizationId == null) {
            throw new Exceptions(
                    "error.noPudoDeterminarOrganizacionActual",
                    HttpStatus.BAD_REQUEST
            );
        }

        return organizationId;
    }

    private FinancialFund findOwn(UUID id) {

        FinancialFund fund =
                financialFundRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.fondoNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (!authContext.isSystem()
                && !fund.getOrganization().getId().equals(currentOrganizationId())) {

            throw new Exceptions(
                    "error.noTieneAccesoFondo",
                    HttpStatus.FORBIDDEN
            );
        }

        return fund;
    }

    @Override
    @Transactional
    public FinancialFundResponse create(FinancialFundRequest request) {

        assertCanManage();

        Organization organization =
                organizationRepository.findById(currentOrganizationId())
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.organizacionNoEncontrada",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        String code = request.getCode().trim().toUpperCase();

        if (financialFundRepository.existsByOrganizationIdAndCodeIgnoreCase(organization.getId(), code)) {
            throw new Exceptions(
                    "error.financialFundCodeAlreadyExists",
                    HttpStatus.BAD_REQUEST
            );
        }

        FinancialFund fund = new FinancialFund();

        fund.setOrganization(organization);
        fund.setCode(code);
        fund.setNameEs(request.getNameEs());
        fund.setNameEn(request.getNameEn());
        fund.setDescription(request.getDescription());
        fund.setStatus(StatusType.ACTIVE);

        return financialFundMapper.simple(
                financialFundRepository.save(fund),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional
    public FinancialFundResponse update(UUID id, FinancialFundRequest request) {

        assertCanManage();

        FinancialFund fund = findOwn(id);

        if (request.getCode() != null) {

            String code = request.getCode().trim().toUpperCase();

            if (financialFundRepository.existsByOrganizationIdAndCodeIgnoreCaseAndIdNot(
                    fund.getOrganization().getId(), code, id)) {

                throw new Exceptions(
                        "error.financialFundCodeAlreadyExists",
                        HttpStatus.BAD_REQUEST
                );
            }

            fund.setCode(code);
        }

        if (request.getNameEs() != null) {
            fund.setNameEs(request.getNameEs());
        }

        if (request.getNameEn() != null) {
            fund.setNameEn(request.getNameEn());
        }

        fund.setDescription(request.getDescription());
        fund.setUpdatedAt(Instant.now());

        return financialFundMapper.simple(
                financialFundRepository.save(fund),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional
    public FinancialFundResponse enable(UUID id) {

        assertCanManage();

        FinancialFund fund = findOwn(id);

        fund.setStatus(StatusType.ACTIVE);
        fund.setUpdatedAt(Instant.now());

        return financialFundMapper.simple(
                financialFundRepository.save(fund),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional
    public FinancialFundResponse disable(UUID id) {

        assertCanManage();

        FinancialFund fund = findOwn(id);

        fund.setStatus(StatusType.INACTIVE);
        fund.setUpdatedAt(Instant.now());

        return financialFundMapper.simple(
                financialFundRepository.save(fund),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialFundResponse getById(UUID id) {

        assertCanManage();

        return financialFundMapper.simple(
                findOwn(id),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<FinancialFundResponse> listActive() {

        boolean showAudit = authContext.canViewAudit();

        return financialFundRepository.findByOrganizationIdAndStatusOrderByNameEsAsc(
                        currentOrganizationId(),
                        StatusType.ACTIVE
                )
                .stream()
                .map(f -> financialFundMapper.simple(f, showAudit))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FinancialFundResponse> listAll() {

        assertCanManage();

        boolean showAudit = authContext.canViewAudit();

        return financialFundRepository.findByOrganizationIdOrderByNameEsAsc(
                        currentOrganizationId()
                )
                .stream()
                .map(f -> financialFundMapper.simple(f, showAudit))
                .toList();
    }
}
