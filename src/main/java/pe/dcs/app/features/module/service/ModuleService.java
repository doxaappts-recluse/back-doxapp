package pe.dcs.app.features.module.service;

import pe.dcs.app.features.module.request.ModuleRequest;
import pe.dcs.app.features.module.request.ModuleSearchRequest;
import pe.dcs.app.features.module.response.ModuleOptionResponse;
import pe.dcs.app.features.module.response.ModuleResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

public interface ModuleService {

    PageResponse<ModuleResponse> search(ModuleSearchRequest request);

    ModuleResponse create(ModuleRequest request);

    ModuleResponse update(UUID id, ModuleRequest request);

    List<ModuleOptionResponse> getParentModules(UUID currentId);

    List<ModuleOptionResponse> getChildModules(UUID currentId);

    ModuleResponse getById(UUID id);

    ModuleResponse enable(UUID id);

    ModuleResponse disable(UUID id);

    ModuleResponse delete(UUID id);
}