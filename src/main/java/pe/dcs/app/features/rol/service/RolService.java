package pe.dcs.app.features.rol.service;

import pe.dcs.app.features.rol.response.RoleResponse;

import java.util.List;

public interface RolService {
    List<RoleResponse> getAll();
    List<RoleResponse> getSystemRoles();
    List<RoleResponse> getOrganizationRoles();


}
