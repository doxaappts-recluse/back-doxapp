package pe.dcs.app.features.user.org_user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.entity.User;
import pe.dcs.app.features.user.org_user.mapper.OrgUserCreateMapper;
import pe.dcs.app.features.user.org_user.mapper.OrgUserMapper;
import pe.dcs.app.features.user.org_user.request.OrgUserCreateRequest;
import pe.dcs.app.features.user.org_user.request.OrgUserListRequest;
import pe.dcs.app.features.user.org_user.request.OrgUserUpdateRequest;
import pe.dcs.app.features.user.org_user.response.OrgUserCreateResponse;
import pe.dcs.app.features.user.org_user.response.OrgUserResponse;
import pe.dcs.app.features.user.org_user.service.OrgUserService;
import pe.dcs.app.repository.UserRepository;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.pagination.PageableUtil;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrgUserServiceImpl implements OrgUserService {

    private final UserRepository userRepository;
    private final AuthContext authContext;


    @Override
    public PageResponse<OrgUserResponse> search(OrgUserListRequest request) {

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        UUID organizationId = authContext.getOrganizationId();

        var filters = request.getFilters();

        Page<User> page = userRepository.findAll(
                OrgUserSpecification.filter(
                        organizationId,
                        filters != null ? filters.getName() : null,
                        filters != null ? filters.getLastname() : null,
                        filters != null ? filters.getSex() : null,
                        filters != null ? filters.getDni() : null
                ),
                pageable
        );

        List<OrgUserResponse> content =
                page.getContent()
                        .stream()
                        .map(OrgUserMapper::toResponse)
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

    @Override
    @Transactional
    public OrgUserResponse create(OrgUserCreateRequest request) {

        UUID organizationId = authContext.getOrganizationId();

        // 1. validar duplicado DNI
        if (userRepository.existsByDniAndOrganizationId(
                request.getDni(),
                organizationId
        )) {
            throw new Exceptions("DNI already exists in this organization", HttpStatus.CONFLICT);
        }

        // 2. crear usuario
        User user = new User();
        user.setName(request.getName());
        user.setLastname(request.getLastname());
        user.setDni(request.getDni());
        user.setSex(request.getSex());
        user.setPhone(request.getPhone());
        user.setAddress(request.getAddress());
        user.setDateBirth(request.getDateBirth());
        user.setMaritalStatus(request.getMaritalStatus());
        user.setChildren(request.getChildren());
        user.setDateAdmission(request.getDateAdmission());

        // organization (obligatorio)
        Organization org = new Organization();
        org.setId(organizationId);
        user.setOrganization(org);

        user = userRepository.save(user);

        return OrgUserMapper.toResponse(user);
    }

    @Override
    @Transactional
    public OrgUserResponse update(UUID id, OrgUserUpdateRequest request) {

        // 1. validar existencia
        User user = userRepository.findById(id)
                .orElseThrow(() -> new Exceptions(
                        "User not found",
                        HttpStatus.NOT_FOUND
                ));

        UUID organizationId = authContext.getOrganizationId();

        // 2. validar pertenece a organización
        if (!user.getOrganization().getId().equals(organizationId)) {
            throw new Exceptions(
                    "User does not belong to organization",
                    HttpStatus.FORBIDDEN
            );
        }

        // 3. validar DNI duplicado excluyendo el mismo usuario
        if (request.getDni() != null &&
                userRepository.existsByDniAndOrganizationIdAndIdNot(
                        request.getDni(),
                        organizationId,
                        id
                )) {
            throw new Exceptions(
                    "DNI already exists in this organization",
                    HttpStatus.CONFLICT
            );
        }

        // 4. update fields (patch style)
        if (request.getName() != null) user.setName(request.getName());
        if (request.getLastname() != null) user.setLastname(request.getLastname());
        if (request.getDni() != null) user.setDni(request.getDni());
        if (request.getSex() != null) user.setSex(request.getSex());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getAddress() != null) user.setAddress(request.getAddress());
        if (request.getDateBirth() != null) user.setDateBirth(request.getDateBirth());
        if (request.getMaritalStatus() != null) user.setMaritalStatus(request.getMaritalStatus());
        if (request.getChildren() != null) user.setChildren(request.getChildren());
        if (request.getDateAdmission() != null) user.setDateAdmission(request.getDateAdmission());

        user = userRepository.save(user);

        return OrgUserMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public OrgUserCreateResponse getById(UUID id) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new Exceptions(
                        "User not found",
                        HttpStatus.NOT_FOUND
                ));

        UUID organizationId = authContext.getOrganizationId();

        if (!user.getOrganization().getId().equals(organizationId)) {
            throw new Exceptions(
                    "User does not belong to organization",
                    HttpStatus.FORBIDDEN
            );
        }

        return OrgUserCreateMapper.toResponse(user);
    }

}