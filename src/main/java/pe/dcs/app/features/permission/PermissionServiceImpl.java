package pe.dcs.app.features.permission;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.permission.response.PermissionResponse;
import pe.dcs.app.features.permission.mapper.PermissionMapper;
import pe.dcs.app.features.permission.service.PermissionService;
import pe.dcs.app.repository.PermissionRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;

    @Override
    public List<PermissionResponse> findAll() {

        return permissionRepository.findAll()
                .stream()
                .map(PermissionMapper::toResponse)
                .toList();
    }
}