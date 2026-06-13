package pe.dcs.app.features.module;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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

import java.util.*;

@Service
@RequiredArgsConstructor
public class ModuleServiceImpl implements ModuleService {

    private final ModuleRepository moduleRepository;
    private final ModuleMapper moduleMapper;


    // =====================================================
    // RESTO SIN CAMBIOS
    // =====================================================

    @Override
    public PageResponse<ModuleResponse> search(ModuleSearchRequest request) {
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

        return new PageResponse<>(
                page.getContent().stream().map(moduleMapper::simple).toList(),
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
        Module module = moduleRepository.findById(id)
                .orElseThrow(() -> new Exceptions("Module not found", HttpStatus.NOT_FOUND));

        return moduleMapper.simple(module);
    }

    @Override
    public List<ModuleOptionResponse> getParentModules(UUID currentId) {
        List<Module> modules =
                currentId != null
                        ? moduleRepository.findByParentIsNullAndStatusAndIdNot(StatusType.ACTIVE, currentId)
                        : moduleRepository.findByParentIsNullAndStatus(StatusType.ACTIVE);

        return modules.stream()
                .map(m -> new ModuleOptionResponse(m.getId(), m.getName()))
                .toList();
    }

    @Override
    public List<ModuleOptionResponse> getChildModules(UUID currentId) {
        List<Module> modules =
                currentId != null
                        ? moduleRepository.findByParentIsNotNullAndStatusAndIdNot(StatusType.ACTIVE, currentId)
                        : moduleRepository.findByParentIsNotNullAndStatus(StatusType.ACTIVE);

        return modules.stream()
                .map(m -> new ModuleOptionResponse(m.getId(), m.getName()))
                .toList();
    }

    @Override
    public ModuleResponse create(ModuleRequest request) {
        Module module = new Module();

        module.setName(request.getName());
        module.setCode(request.getCode());
        module.setIcon(request.getIcon());
        module.setRoute(request.getRoute());
        module.setOrderNum(request.getOrderNum());
        module.setStatus(StatusType.ACTIVE);

        if (request.getParentId() != null) {
            Module parent = moduleRepository.findById(request.getParentId())
                    .orElseThrow(() -> new Exceptions("Parent module not found", HttpStatus.NOT_FOUND));

            module.setParent(parent);
        }

        moduleRepository.save(module);

        return moduleMapper.simple(module);
    }

    @Override
    public ModuleResponse update(UUID id, ModuleRequest request) {

        Module module = moduleRepository.findById(id)
                .orElseThrow(() -> new Exceptions("Module not found", HttpStatus.NOT_FOUND));

        module.setName(request.getName());
        module.setCode(request.getCode());
        module.setIcon(request.getIcon());
        module.setRoute(request.getRoute());
        module.setOrderNum(request.getOrderNum());

        if (request.getParentId() != null) {
            Module parent = moduleRepository.findById(request.getParentId())
                    .orElseThrow(() -> new Exceptions("Parent module not found", HttpStatus.NOT_FOUND));

            module.setParent(parent);
        } else {
            module.setParent(null);
        }

        moduleRepository.save(module);

        return moduleMapper.simple(module);
    }

    @Override
    public ModuleResponse enable(UUID id) {
        Module module = moduleRepository.findById(id)
                .orElseThrow(() -> new Exceptions("Module not found", HttpStatus.NOT_FOUND));

        module.setStatus(StatusType.ACTIVE);
        moduleRepository.save(module);

        return moduleMapper.simple(module);
    }

    @Override
    public ModuleResponse disable(UUID id) {
        Module module = moduleRepository.findById(id)
                .orElseThrow(() -> new Exceptions("Module not found", HttpStatus.NOT_FOUND));

        module.setStatus(StatusType.INACTIVE);
        moduleRepository.save(module);

        return moduleMapper.simple(module);
    }

    @Override
    public ModuleResponse delete(UUID id) {
        Module module = moduleRepository.findById(id)
                .orElseThrow(() -> new Exceptions("Module not found", HttpStatus.NOT_FOUND));

        moduleRepository.delete(module);

        return moduleMapper.simple(module);
    }
}