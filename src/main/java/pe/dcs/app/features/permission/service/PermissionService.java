package pe.dcs.app.features.permission.service;

import pe.dcs.app.features.permission.response.PermissionResponse;

import java.util.List;

public interface PermissionService {
    List<PermissionResponse> findAll();
}