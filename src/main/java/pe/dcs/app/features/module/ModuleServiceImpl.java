package pe.dcs.app.features.module;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Module;
import pe.dcs.app.features.module.request.ModuleFilter;
import pe.dcs.app.features.module.request.ModuleRequest;
import pe.dcs.app.features.module.request.ModuleSearchRequest;
import pe.dcs.app.features.module.response.ModuleOptionResponse;
import pe.dcs.app.features.module.response.ModuleResponse;
import pe.dcs.app.repository.ModuleRepository;
import pe.dcs.app.features.module.mapper.ModuleMapper;
import pe.dcs.app.features.module.service.ModuleService;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageableUtil;

import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.security.service.AuthContext;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ModuleServiceImpl implements ModuleService {

    private final ModuleRepository moduleRepository;
    private final ModuleMapper moduleMapper;
    private final AuthContext authContext;

    /**
     * El catálogo de módulos es de gestión exclusiva de SYSTEM
     * (define qué módulos existen para armar contratos). Los
     * módulos que sí ve un org/branch admin (según su contrato
     * activo) se resuelven aparte, en ContractModuleAccessService.
     */
    private void assertSystem() {

        if (!authContext.isSystem()) {

            throw new Exceptions(
                    "error.soloAdministradorSistemaPuedeGestionarCatalogo2",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    @Override
    public PageResponse<ModuleResponse> search(ModuleSearchRequest request) {

        assertSystem();

        Pageable pageable =
                PageableUtil.buildPageable(request.getPagination(), request.getSorts());

        ModuleFilter filters = request.getFilters();

        Specification<Module> spec =
                ModuleSpecification.filter(
                        filters != null ? filters.getName() : null,
                        filters != null ? filters.getCode() : null,
                        filters != null ? filters.getStatus() : null
                );

        Page<Module> page = moduleRepository.findAll(spec, pageable);

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent().stream().map(m -> moduleMapper.simple(m, showAudit)).toList(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    @Override
    public ModuleResponse getById(UUID id) {

        assertSystem();

        Module module = moduleRepository.findById(id)
                .orElseThrow(() -> new Exceptions("error.moduleNotFound", HttpStatus.NOT_FOUND));

        return moduleMapper.simple(module, authContext.canViewAudit());
    }

    @Override
    public List<ModuleOptionResponse> getParentModules(UUID currentId){

        assertSystem();

        List<Module> modules =
                currentId != null
                        ? moduleRepository.findByParentIsNullAndStatusAndIdNotOrderByOrderNumAsc(
                        StatusType.ACTIVE,
                        currentId
                )
                        : moduleRepository.findByParentIsNullAndStatusOrderByOrderNumAsc(
                        StatusType.ACTIVE
                );

        return modules.stream()
                .map(m ->
                        new ModuleOptionResponse(
                                m.getId(),
                                m.getLocalizedName()
                        )
                )
                .toList();
    }

    @Override
    public List<ModuleOptionResponse> getChildModules(UUID currentId){

        assertSystem();

        List<Module> modules =
                currentId != null
                        ? moduleRepository.findByParentIsNotNullAndStatusAndIdNotOrderByOrderNumAsc(
                        StatusType.ACTIVE,
                        currentId
                )
                        : moduleRepository.findByParentIsNotNullAndStatusOrderByOrderNumAsc(
                        StatusType.ACTIVE
                );

        return modules.stream()
                .map(m ->
                        new ModuleOptionResponse(
                                m.getId(),
                                m.getLocalizedName()
                        )
                )
                .toList();
    }

    @Override
    @Transactional
    public ModuleResponse create(ModuleRequest request){

        assertSystem();

        if(moduleRepository.existsByCodeIgnoreCase(request.getCode())){
            throw new Exceptions(
                    "error.moduleCodeAlreadyExists",
                    HttpStatus.BAD_REQUEST
            );
        }

        Module module = new Module();

        module.setNameEs(request.getNameEs());
        module.setNameEn(request.getNameEn());
        module.setCode(request.getCode().trim().toUpperCase());
        module.setIcon(request.getIcon());
        module.setRoute(request.getRoute());
        module.setOrderNum(request.getOrderNum());
        module.setStatus(StatusType.ACTIVE);

        module.setVisibleSystem(defaultTrue(request.getVisibleSystem()));
        module.setVisibleOrgAdmin(defaultTrue(request.getVisibleOrgAdmin()));
        module.setVisibleBranchAdmin(defaultTrue(request.getVisibleBranchAdmin()));
        module.setVisibleUser(defaultTrue(request.getVisibleUser()));

        if(request.getParentId() != null){

            Module parent =
                    moduleRepository.findById(request.getParentId())
                            .orElseThrow(() ->
                                    new Exceptions(
                                            "error.parentModuleNotFound",
                                            HttpStatus.NOT_FOUND
                                    )
                            );

            if(!parent.isActive()){
                throw new Exceptions(
                        "error.parentModuleInactive",
                        HttpStatus.BAD_REQUEST
                );
            }

            /*
             * Solo se permiten
             * dos niveles.
             */

            if(parent.getParent() != null){
                throw new Exceptions(
                        "error.onlyOneNestingLevelAllowed",
                        HttpStatus.BAD_REQUEST
                );
            }

            module.setParent(parent);
        }

        moduleRepository.save(module);

        return moduleMapper.simple(module, authContext.canViewAudit());
    }

    @Override
    @Transactional
    public ModuleResponse update(
            UUID id,
            ModuleRequest request
    ){

        assertSystem();

        Module module =
                moduleRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.moduleNotFound",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        // =====================================================
        // CODE IS IMMUTABLE
        // =====================================================

        if(!module.getCode().equalsIgnoreCase(request.getCode())){
            throw new Exceptions(
                    "error.moduleCodeCannotModified",
                    HttpStatus.BAD_REQUEST
            );
        }

        // =====================================================
        // VALIDATE PARENT CHANGE
        // =====================================================

        UUID currentParentId =
                module.getParent() != null
                        ? module.getParent().getId()
                        : null;

        UUID newParentId = request.getParentId();

        if (!Objects.equals(currentParentId, newParentId)) {

            // El módulo actualmente es padre
            if (currentParentId == null) {

                boolean hasActiveChildren =
                        moduleRepository.existsByParentIdAndStatus(
                                module.getId(),
                                StatusType.ACTIVE
                        );

                if (hasActiveChildren) {
                    throw new Exceptions(
                            "error.noPuedeCambiarModuloPadrePorque",
                            HttpStatus.BAD_REQUEST
                    );
                }

                // Tampoco permitir convertirlo en hijo si no tiene hijos activos
                // (si quieres mantener la jerarquía fija)
                if (newParentId != null) {
                    throw new Exceptions(
                            "error.moduloPadreNoPuedeConvertirseModulo",
                            HttpStatus.BAD_REQUEST
                    );
                }

            } else {

                // Era hijo

                if (newParentId == null) {
                    throw new Exceptions(
                            "error.moduloHijoNoPuedeConvertirseModulo",
                            HttpStatus.BAD_REQUEST
                    );
                }

                Module newParent =
                        moduleRepository.findById(newParentId)
                                .orElseThrow(() ->
                                        new Exceptions(
                                                "error.moduloPadreNoEncontrado",
                                                HttpStatus.NOT_FOUND
                                        )
                                );

                module.setParent(newParent);
            }
        }

        // =====================================================
        // UPDATE ALLOWED FIELDS
        // =====================================================

        module.setNameEs(request.getNameEs());
        module.setNameEn(request.getNameEn());
        module.setIcon(request.getIcon());
        module.setRoute(request.getRoute());
        module.setOrderNum(request.getOrderNum());

        module.setVisibleSystem(defaultTrue(request.getVisibleSystem()));
        module.setVisibleOrgAdmin(defaultTrue(request.getVisibleOrgAdmin()));
        module.setVisibleBranchAdmin(defaultTrue(request.getVisibleBranchAdmin()));
        module.setVisibleUser(defaultTrue(request.getVisibleUser()));

        moduleRepository.save(module);

        return moduleMapper.simple(module, authContext.canViewAudit());
    }

    /**
     * Un frontend viejo (o un request incompleto) que no
     * mande el campo de visibilidad no debería ocultar el
     * módulo por accidente: se trata null como "visible".
     */
    private boolean defaultTrue(Boolean value){
        return value == null || value;
    }

    @Override
    @Transactional
    public ModuleResponse enable(UUID id){

        assertSystem();

        Module module =
                moduleRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.moduleNotFound",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        module.setStatus(StatusType.ACTIVE);

        moduleRepository.save(module);

        return moduleMapper.simple(module, authContext.canViewAudit());
    }

    @Override
    @Transactional
    public ModuleResponse disable(UUID id) {

        assertSystem();

        Module module =
                moduleRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.moduleNotFound",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (module.getStatus() == StatusType.INACTIVE) {
            return moduleMapper.simple(module, authContext.canViewAudit());
        }

        boolean hasActiveChildren =
                moduleRepository.existsByParent_IdAndStatus(
                        module.getId(),
                        StatusType.ACTIVE
                );

        if (hasActiveChildren) {
            throw new Exceptions(
                    "error.cannotDeactivateModuleThatHasActive",
                    HttpStatus.BAD_REQUEST
            );
        }

        module.setStatus(StatusType.INACTIVE);

        moduleRepository.save(module);

        return moduleMapper.simple(module, authContext.canViewAudit());
    }

}