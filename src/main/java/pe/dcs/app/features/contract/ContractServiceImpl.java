package pe.dcs.app.features.contract;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Contract;
import pe.dcs.app.entity.ContractBranchLicense;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.features.contract.mapper.ContractMapper;
import pe.dcs.app.features.contract.request.ContractBranchLicenseRequest;
import pe.dcs.app.features.contract.request.ContractCreateRequest;
import pe.dcs.app.features.contract.request.ContractFilterRequest;
import pe.dcs.app.features.contract.request.ContractListRequest;
import pe.dcs.app.features.contract.request.ContractModuleRequest;
import pe.dcs.app.features.contract.request.ContractUpdateRequest;
import pe.dcs.app.features.contract.response.ContractBranchLicenseResponse;
import pe.dcs.app.features.contract.response.ContractModuleConfigResponse;
import pe.dcs.app.features.contract.response.ContractPermissionConfigResponse;
import pe.dcs.app.features.contract.response.ContractResponse;
import pe.dcs.app.features.contract.response.ContractResponseSearch;
import pe.dcs.app.features.contract.service.ContractModuleService;
import pe.dcs.app.features.contract.service.ContractService;
import pe.dcs.app.features.module.ContractResolver;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.ContractBranchLicenseRepository;
import pe.dcs.app.repository.ContractModuleRepository;
import pe.dcs.app.repository.ContractRepository;
import pe.dcs.app.repository.OrganizationRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.contract.ContractRenewalType;
import pe.dcs.app.util.enums.contract.ContractScope;
import pe.dcs.app.util.enums.contract.ContractSort;
import pe.dcs.app.util.enums.contract.ContractStatus;
import pe.dcs.app.util.enums.contract.LicenseDistributionMode;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ContractServiceImpl implements ContractService {

    private final ContractRepository contractRepository;
    private final OrganizationRepository organizationRepository;
    private final BranchRepository branchRepository;
    private final ContractBranchLicenseRepository contractBranchLicenseRepository;
    private final ContractModuleService contractModuleService;
    private final ContractModuleRepository contractModuleRepository;
    private final ContractResolver contractResolver;
    private final AuthContext authContext;

    // =====================================================
    // SEARCH / HISTORIAL
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ContractResponseSearch> search(
            ContractListRequest request
    ) {

        assertSystemUser();

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts(),
                        ContractSort::resolvePath
                );

        Page<Contract> page =
                contractRepository.findAll(
                        ContractSpecification.filter(
                                request.getFilters()
                        ),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        List<ContractResponseSearch> content =
                page.getContent()
                        .stream()
                        .map(contract -> ContractMapper.toResponseSearch(contract, showAudit))
                        .toList();

        return new PageResponse<>(
                content,
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    /**
     * Sin assertSystemUser() a propósito: cualquier usuario con
     * contexto org/sede (org admin, branch admin, org user) puede
     * consultar SUS PROPIOS módulos contratados — el caller no
     * elige de qué organización, siempre es la del contexto actual
     * (ver AuthContext.getCurrentBranchId()). Reutiliza la misma
     * resolución que SidebarService (unión de contrato de
     * organización + contrato de sede, ver
     * ContractResolver.getActiveContractsByBranch), así que un
     * módulo cuenta como "contratado" con el mismo criterio con el
     * que se decide si aparece en el sidebar.
     */
    @Override
    @Transactional(readOnly = true)
    public List<String> getActiveModuleCodesForCurrentContext() {

        UUID branchId = authContext.getCurrentBranchId();

        return contractResolver.getActiveContractsByBranch(branchId)
                .stream()
                .flatMap(contract ->
                        contractModuleRepository
                                .findActiveByContractId(contract.getId())
                                .stream()
                )
                .map(contractModule -> contractModule.getModule().getCode())
                .distinct()
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ContractResponseSearch> historyByOrganization(
            UUID organizationId,
            ContractListRequest request
    ) {

        if (request.getFilters() == null) {
            request.setFilters(new ContractFilterRequest());
        }

        request.getFilters().setOrganizationId(organizationId);
        request.getFilters().setBranchId(null);

        return search(request);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ContractResponseSearch> historyByBranch(
            UUID branchId,
            ContractListRequest request
    ) {

        if (request.getFilters() == null) {
            request.setFilters(new ContractFilterRequest());
        }

        request.getFilters().setBranchId(branchId);
        request.getFilters().setOrganizationId(null);

        return search(request);
    }

    // =====================================================
    // GET BY ID
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public ContractResponse getById(UUID id) {

        assertSystemUser();

        Contract contract = getContract(id);

        return buildDetailResponse(contract);
    }

    // =====================================================
    // CREATE
    // =====================================================

    @Override
    @Transactional
    public ContractResponse create(ContractCreateRequest request) {

        assertSystemUser();

        Organization organization = getOrganization(
                request.getOrganizationId()
        );

        if (request.getScope() == null) {
            throw new Exceptions(
                    "error.debeIndicarAlcanceContratoOrganizacionSede",
                    HttpStatus.BAD_REQUEST
            );
        }

        Contract contract = new Contract();

        contract.setOrganization(organization);
        contract.setScope(request.getScope());

        applyScope(contract, organization, request.getScope(), request.getBranchId());

        applyPlanFields(
                contract,
                request.getPlanName(),
                request.getPrice(),
                request.getCurrency(),
                request.getStartDate(),
                request.getEndDate(),
                request.getMaxLicenses(),
                request.getDistributionMode(),
                request.getRenewalType()
        );

        if (request.getPreviousContractId() != null) {

            Contract previous = getContract(
                    request.getPreviousContractId()
            );

            contract.setPreviousContract(previous);
        }

        contract.setStatus(
                request.getStartDate().isAfter(LocalDate.now())
                        ? ContractStatus.PENDING
                        : ContractStatus.ACTIVE
        );

        if (contract.getStatus() == ContractStatus.ACTIVE) {
            contract.setActivatedAt(Instant.now());
        }

        validateNoOverlap(contract, null);

        contract = contractRepository.save(contract);

        replaceBranchLicenses(
                contract,
                request.getBranchLicenses()
        );

        contractModuleService.replaceModules(
                contract,
                request.getModules()
        );

        return buildDetailResponse(contract);
    }

    // =====================================================
    // UPDATE
    // =====================================================

    @Override
    @Transactional
    public ContractResponse update(UUID id, ContractUpdateRequest request) {

        assertSystemUser();

        Contract contract = getContract(id);

        assertEditable(contract);

        /*
         * PENDING (todavía no arrancó): edición 100% libre, sin
         * restricciones ni versionado. No hay ni un día de historial
         * real que proteger, así que hasta un cambio de plan/precio/
         * módulos se guarda en el mismo registro. Acá SÍ se respeta
         * el renewalType que venga en el request tal cual (incluso
         * si el admin lo corrige antes de que el contrato arranque).
         */
        if (contract.getStatus() == ContractStatus.PENDING) {
            return applyInPlaceUpdate(contract, request, request.getRenewalType());
        }

        /*
         * ACTIVE / SUSPENDED (los únicos que llegan hasta acá:
         * CANCELLED/EXPIRED/REPLACED ya los bloqueó assertEditable).
         *
         * El versionado NO se infiere comparando campos: lo decide
         * explícitamente el admin al elegir "Renovación", "Upgrade"
         * o "Downgrade" en renewalType, y solo cuando de verdad lo
         * está CAMBIANDO a uno de esos valores en este guardado (no
         * cuando ya venía así de antes). En ese caso no se edita
         * in-place: se crea un contrato nuevo con los términos
         * nuevos, enlazado via previousContract, para que lo que ya
         * estuvo vigente (y pudo haberse facturado/reportado con
         * esos términos) quede intacto en el historial. Ver
         * contract-status-rules.config en el front y el comentario
         * de ContractStatus.REPLACED.
         */
        boolean isVersioningTransition =
                request.getRenewalType() != null
                        && request.getRenewalType() != contract.getRenewalType()
                        && (request.getRenewalType() == ContractRenewalType.RENEWAL
                                || request.getRenewalType() == ContractRenewalType.UPGRADE
                                || request.getRenewalType() == ContractRenewalType.DOWNGRADE);

        if (isVersioningTransition) {

            Optional<ContractResponse> versioned =
                    tryReplaceWithNewVersion(contract, request);

            if (versioned.isPresent()) {
                return versioned.get();
            }

            /*
             * El contrato todavía no vivió ni un día bajo sus
             * términos actuales (recién arrancó hoy): no hay nada
             * real que preservar como historial, así que en este
             * caso puntual se sigue como corrección normal abajo.
             */
        }

        /*
         * Corrección normal sobre un contrato YA vigente: el
         * renewalType es el "origen" de este contrato (cómo llegó a
         * existir) y no se pisa con lo que venga en el request salvo
         * que arriba se haya detectado una transición real; se
         * preserva tal cual. Y no se permite cambiar plan/precio/
         * moneda/licencias/módulos calladamente: si el contrato ya
         * está vigente, eso es un cambio comercial y tiene que
         * declararse como Renovación/Upgrade/Downgrade.
         */
        if (isCommercialChange(contract, request)) {
            throw new Exceptions(
                    "error.contratoVigenteCambiarPlanPrecioModulos",
                    HttpStatus.CONFLICT
            );
        }

        return applyInPlaceUpdate(contract, request, contract.getRenewalType());
    }

    /**
     * Edición in-place compartida: PENDING (sin restricciones) y
     * correcciones no comerciales sobre contratos ya vigentes.
     */
    private ContractResponse applyInPlaceUpdate(
            Contract contract,
            ContractUpdateRequest request,
            ContractRenewalType renewalType
    ) {

        applyPlanFields(
                contract,
                request.getPlanName(),
                request.getPrice(),
                request.getCurrency(),
                request.getStartDate(),
                request.getEndDate(),
                request.getMaxLicenses(),
                request.getDistributionMode(),
                renewalType
        );

        validateNoOverlap(contract, contract.getId());

        contract = contractRepository.save(contract);

        replaceBranchLicenses(
                contract,
                request.getBranchLicenses()
        );

        contractModuleService.replaceModules(
                contract,
                request.getModules()
        );

        return buildDetailResponse(contract);
    }

    /**
     * Compara lo YA asignado (vía el mismo catálogo que arma el
     * formulario) contra lo que llega en el request. Cualquier
     * cambio de plan/precio/moneda/licencias, o módulo/permiso
     * agregado o quitado, cuenta como cambio comercial. Solo se usa
     * para VALIDAR (rechazar corrección silenciosa sobre un contrato
     * vigente); el disparador real del versionado es renewalType.
     */
    private boolean isCommercialChange(
            Contract contract,
            ContractUpdateRequest request
    ) {

        if (!Objects.equals(contract.getPlanName(), request.getPlanName())) {
            return true;
        }

        if (contract.getPrice().compareTo(
                request.getPrice() != null ? request.getPrice() : contract.getPrice()
        ) != 0) {
            return true;
        }

        if (!Objects.equals(contract.getCurrency(), request.getCurrency())) {
            return true;
        }

        if (!Objects.equals(contract.getMaxLicenses(), request.getMaxLicenses())) {
            return true;
        }

        LicenseDistributionMode requestedDistribution =
                contract.isOrganizationScope() && request.getDistributionMode() != null
                        ? request.getDistributionMode()
                        : LicenseDistributionMode.SHARED;

        if (contract.getDistributionMode() != requestedDistribution) {
            return true;
        }

        return modulesChanged(contract, request.getModules());
    }

    private boolean modulesChanged(
            Contract contract,
            List<ContractModuleRequest> requestedModules
    ) {

        Map<UUID, Set<UUID>> current =
                contractModuleService.getCatalog(contract.getId())
                        .stream()
                        .filter(ContractModuleConfigResponse::isAssigned)
                        .collect(
                                Collectors.toMap(
                                        ContractModuleConfigResponse::getModuleId,
                                        m -> m.getPermissions()
                                                .stream()
                                                .filter(ContractPermissionConfigResponse::isAssigned)
                                                .map(ContractPermissionConfigResponse::getId)
                                                .collect(Collectors.toSet())
                                )
                        );

        Map<UUID, Set<UUID>> requested =
                (requestedModules == null ? List.<ContractModuleRequest>of() : requestedModules)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        ContractModuleRequest::getModuleId,
                                        r -> r.getPermissionIds() == null
                                                ? Set.<UUID>of()
                                                : new HashSet<>(r.getPermissionIds())
                                )
                        );

        return !current.equals(requested);
    }

    // =====================================================
    // VERSIONADO (renovación / upgrade / downgrade)
    // =====================================================

    private Optional<ContractResponse> tryReplaceWithNewVersion(
            Contract oldContract,
            ContractUpdateRequest request
    ) {

        if (request.getEndDate() == null) {
            throw new Exceptions(
                    "error.fechaFinObligatoria",
                    HttpStatus.BAD_REQUEST
            );
        }

        LocalDate today = LocalDate.now();
        boolean isRenewal = request.getRenewalType() == ContractRenewalType.RENEWAL;

        /*
         * ¿A partir de cuándo rige el contrato nuevo? request.
         * getStartDate() NO se usa acá a propósito: el front no
         * deja editar la fecha de inicio cuando se está
         * renovando/subiendo/bajando de plan, se calcula sola:
         *
         * - RENEWAL: sigue justo donde termina el contrato actual
         *   (oldContract.endDate + 1), sin importar qué día es hoy.
         *   Así una renovación hecha unos días ANTES de que venza
         *   no le corta vigencia a lo que ya está pagado/vigente.
         * - UPGRADE / DOWNGRADE: rige desde HOY (el cambio de plan
         *   es inmediato), salvo que el contrato ni siquiera haya
         *   empezado todavía (no debería pasar en la práctica, pero
         *   por las dudas no se ancla antes de su propio inicio).
         */
        LocalDate newStart =
                isRenewal
                        ? oldContract.getEndDate().plusDays(1)
                        : (today.isAfter(oldContract.getStartDate())
                                ? today
                                : oldContract.getStartDate());

        LocalDate closingDate = newStart.minusDays(1);

        if (closingDate.isBefore(oldContract.getStartDate())) {

            /*
             * El nuevo arranque cae el mismo día (o antes) de que
             * empezó el contrato viejo: no hubo ni un día de
             * historial real bajo los términos actuales. No tiene
             * sentido versionar sobre la nada; que se maneje como
             * edición in-place normal.
             */
            return Optional.empty();
        }

        /*
         * ¿Ya rige HOY, o es una renovación programada a futuro?
         *
         * - Upgrade/Downgrade: newStart siempre es hoy (o antes),
         *   así que esto es siempre true para ellos.
         * - Renewal: normalmente newStart es futuro (oldContract.
         *   endDate todavía no llegó) -> el contrato viejo NO se
         *   toca para nada, sigue ACTIVE/SUSPENDED y 100% editable
         *   hasta que de verdad le toque terminar; el cierre real
         *   (REPLACED) lo hace el scheduler el día que el nuevo se
         *   activa (ver ContractScheduler). Si la renovación se
         *   hace tarde (oldContract.endDate ya pasó), newStart cae
         *   hoy o antes: ahí sí hay que cerrar el viejo ya mismo.
         */
        boolean takesEffectNow = !newStart.isAfter(today);

        if (takesEffectNow) {

            oldContract.setEndDate(closingDate);
            oldContract.markReplaced();

            /*
             * Flush explícito: el contrato nuevo se valida contra
             * solapamiento (validateNoOverlap) justo después, y esa
             * consulta lee directo de la BD. No queremos depender
             * del auto-flush de Hibernate para que el cierre de
             * este contrato ya sea visible en ese SELECT (misma
             * lección que el fix de
             * ContractModuleServiceImpl.replaceModules).
             */
            contractRepository.saveAndFlush(oldContract);
        }

        Contract newContract = new Contract();

        newContract.setOrganization(oldContract.getOrganization());
        newContract.setScope(oldContract.getScope());
        newContract.setBranch(oldContract.getBranch());
        newContract.setPreviousContract(oldContract);

        applyPlanFields(
                newContract,
                request.getPlanName(),
                request.getPrice(),
                request.getCurrency(),
                newStart,
                request.getEndDate(),
                request.getMaxLicenses(),
                request.getDistributionMode(),
                request.getRenewalType()
        );

        newContract.setStatus(
                takesEffectNow
                        ? ContractStatus.ACTIVE
                        : ContractStatus.PENDING
        );

        if (newContract.getStatus() == ContractStatus.ACTIVE) {
            newContract.setActivatedAt(Instant.now());
        }

        validateNoOverlap(newContract, null);

        newContract = contractRepository.save(newContract);

        replaceBranchLicenses(
                newContract,
                request.getBranchLicenses()
        );

        contractModuleService.replaceModules(
                newContract,
                request.getModules()
        );

        return Optional.of(buildDetailResponse(newContract));
    }

    // =====================================================
    // TRANSICIONES DE ESTADO
    // =====================================================

    @Override
    @Transactional
    public void activate(UUID id) {

        assertSystemUser();

        Contract contract =
                contractRepository.findByIdForUpdate(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.contratoNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        validateNoOverlapForActivation(contract, contract.getId());

        applyTransition(contract::activate);
    }

    @Override
    @Transactional
    public void reactivate(UUID id) {
        activate(id);
    }

    @Override
    @Transactional
    public void suspend(UUID id) {

        assertSystemUser();

        Contract contract = getContract(id);

        applyTransition(contract::suspend);
    }

    @Override
    @Transactional
    public void cancel(UUID id) {

        assertSystemUser();

        Contract contract = getContract(id);

        applyTransition(contract::cancel);
    }

    private void applyTransition(Runnable transition) {

        try {

            transition.run();

        } catch (IllegalStateException ex) {

            throw new Exceptions(
                    ex.getMessage(),
                    HttpStatus.CONFLICT
            );
        }
    }

    // =====================================================
    // HELPERS - SCOPE / PLAN
    // =====================================================

    private void applyScope(
            Contract contract,
            Organization organization,
            ContractScope scope,
            UUID branchId
    ) {

        if (scope == ContractScope.ORGANIZATION) {

            contract.setBranch(null);
            return;
        }

        if (branchId == null) {
            throw new Exceptions(
                    "error.debeIndicarSedeContratoSede",
                    HttpStatus.BAD_REQUEST
            );
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() ->
                        new Exceptions(
                                "error.sedeNoEncontrada2",
                                HttpStatus.NOT_FOUND
                        )
                );

        if (!branch.getOrganization().getId().equals(organization.getId())) {

            throw new Exceptions(
                    "error.sedeNoPerteneceOrganizacionIndicada",
                    HttpStatus.BAD_REQUEST
            );
        }

        contract.setBranch(branch);
    }

    private void applyPlanFields(
            Contract contract,
            String planName,
            BigDecimal price,
            String currency,
            LocalDate startDate,
            LocalDate endDate,
            Integer maxLicenses,
            LicenseDistributionMode distributionMode,
            ContractRenewalType renewalType
    ) {

        if (planName == null || planName.isBlank()) {
            throw new Exceptions(
                    "error.elPlanEsObligatorio",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (startDate == null || endDate == null) {
            throw new Exceptions(
                    "error.fechasVigenciaSonObligatorias",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (renewalType == null) {
            throw new Exceptions(
                    "error.tipoRenovacionObligatorio",
                    HttpStatus.BAD_REQUEST
            );
        }

        contract.setPlanName(planName);
        contract.setPrice(price);
        contract.setCurrency(currency);
        contract.setStartDate(startDate);
        contract.setEndDate(endDate);
        contract.setMaxLicenses(maxLicenses);
        contract.setRenewalType(renewalType);

        contract.setDistributionMode(
                contract.isOrganizationScope() && distributionMode != null
                        ? distributionMode
                        : LicenseDistributionMode.SHARED
        );
    }

    // =====================================================
    // HELPERS - OVERLAP
    // =====================================================

    /**
     * Un contrato SUSPENDED está, por definición, apagado: no
     * está rigiendo esas fechas ahora mismo, así que no debería
     * bloquear la creación de uno nuevo que las use. Solo ACTIVE
     * (rige ahora) y PENDING (va a regir en el futuro) representan
     * fechas realmente "ocupadas".
     */
    private static final Set<ContractStatus> OCCUPYING_STATUSES_ON_SAVE = Set.of(
            ContractStatus.ACTIVE,
            ContractStatus.PENDING
    );

    /**
     * Al ACTIVAR (manual, desde PENDING o SUSPENDED) solo importa
     * no pisar un contrato que HOY está ACTIVE — no tiene sentido
     * bloquear la activación por otro PENDING/SUSPENDED que ni
     * siquiera está rigiendo.
     */
    private static final Set<ContractStatus> OCCUPYING_STATUSES_ON_ACTIVATION = Set.of(
            ContractStatus.ACTIVE
    );

    private void validateNoOverlap(Contract contract, UUID excludedContractId) {

        validateNoOverlap(
                contract,
                excludedContractId,
                OCCUPYING_STATUSES_ON_SAVE,
                "error.existeContratoActivoPendienteSolapa"
        );
    }

    private void validateNoOverlapForActivation(Contract contract, UUID excludedContractId) {

        validateNoOverlap(
                contract,
                excludedContractId,
                OCCUPYING_STATUSES_ON_ACTIVATION,
                "error.existeContratoActivoSolapaActivacion"
        );
    }

    private void validateNoOverlap(
            Contract contract,
            UUID excludedContractId,
            Set<ContractStatus> occupyingStatuses,
            String errorKey
    ) {

        List<Contract> candidates =
                contract.isBranchScope()
                        ? contractRepository.findOverlappingContracts(
                        contract.getBranch().getId(),
                        contract.getStartDate(),
                        contract.getEndDate()
                )
                        : contractRepository.findOverlappingOrganizationContracts(
                        contract.getOrganization().getId(),
                        contract.getStartDate(),
                        contract.getEndDate()
                );

        boolean hasConflict =
                candidates.stream()
                        .filter(c -> excludedContractId == null || !c.getId().equals(excludedContractId))
                        .filter(c -> occupyingStatuses.contains(c.getStatus()))
                        .anyMatch(contract::overlapsWith);

        if (hasConflict) {
            throw new Exceptions(
                    errorKey,
                    HttpStatus.CONFLICT
            );
        }
    }

    // =====================================================
    // HELPERS - LICENCIAS POR SEDE
    // =====================================================

    private void replaceBranchLicenses(
            Contract contract,
            List<ContractBranchLicenseRequest> requests
    ) {

        contractBranchLicenseRepository.deleteByContractId(
                contract.getId()
        );

        if (contract.getDistributionMode() != LicenseDistributionMode.ALLOCATED
                || !contract.isOrganizationScope()) {
            return;
        }

        if (requests == null || requests.isEmpty()) {
            return;
        }

        Set<UUID> seenBranches = new HashSet<>();
        int total = 0;

        for (ContractBranchLicenseRequest req : requests) {

            if (req.getBranchId() == null) {
                throw new Exceptions(
                        "error.sedeInvalidaRepartoLicencias",
                        HttpStatus.BAD_REQUEST
                );
            }

            if (!seenBranches.add(req.getBranchId())) {
                throw new Exceptions(
                        "error.noPuedeRepartirLicenciasDosVeces",
                        HttpStatus.BAD_REQUEST
                );
            }

            if (req.getAllocatedLicenses() == null || req.getAllocatedLicenses() < 0) {
                throw new Exceptions(
                        "error.cantidadLicenciasDebeSerMayorIgual",
                        HttpStatus.BAD_REQUEST
                );
            }

            Branch branch = branchRepository.findById(req.getBranchId())
                    .orElseThrow(() ->
                            new Exceptions(
                                    "error.sedeNoEncontrada2",
                                    HttpStatus.NOT_FOUND
                            )
                    );

            if (!branch.getOrganization().getId().equals(contract.getOrganization().getId())) {

                throw new Exceptions(
                        "error.laSedeNoPerteneceOrganizacionContrato",
                        HttpStatus.BAD_REQUEST,
                        branch.getName()
                );
            }

            total += req.getAllocatedLicenses();

            ContractBranchLicense license = new ContractBranchLicense();

            license.setContract(contract);
            license.setBranch(branch);
            license.setAllocatedLicenses(req.getAllocatedLicenses());

            contractBranchLicenseRepository.save(license);
        }

        if (contract.getMaxLicenses() != null && total > contract.getMaxLicenses()) {

            throw new Exceptions(
                    "error.repartoLicenciasSedeSuperaMaximo",
                    HttpStatus.BAD_REQUEST,
                    total, contract.getMaxLicenses()
            );
        }
    }

    // =====================================================
    // HELPERS - RESPUESTA DETALLE
    // =====================================================

    private ContractResponse buildDetailResponse(Contract contract) {

        List<ContractBranchLicenseResponse> branchLicenses =
                contract.isOrganizationScope()
                        ? contractBranchLicenseRepository
                        .findByContractId(contract.getId())
                        .stream()
                        .map(ContractMapper::toBranchLicenseResponse)
                        .toList()
                        : List.of();

        return ContractMapper.toResponse(
                contract,
                contractModuleService.getCatalog(contract.getId()),
                branchLicenses
        );
    }

    // =====================================================
    // HELPERS - CARGA / VALIDACIONES GENERALES
    // =====================================================

    private void assertSystemUser() {

        if (!authContext.isSystem()) {

            throw new Exceptions(
                    "error.soloAdministradorSistemaPuedeGestionarContratos",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private void assertEditable(Contract contract) {

        if (contract.getStatus() == ContractStatus.CANCELLED
                || contract.getStatus() == ContractStatus.EXPIRED
                || contract.getStatus() == ContractStatus.REPLACED) {

            throw new Exceptions(
                    "error.noPuedeEditarContratoCanceladoVencido",
                    HttpStatus.CONFLICT
            );
        }
    }

    private Contract getContract(UUID id) {

        return contractRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions(
                                "error.contratoNoEncontrado",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    private Organization getOrganization(UUID id) {

        if (id == null) {
            throw new Exceptions(
                    "error.laOrganizacionEsObligatoria",
                    HttpStatus.BAD_REQUEST
            );
        }

        return organizationRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions(
                                "error.organizacionNoEncontrada2",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

}
