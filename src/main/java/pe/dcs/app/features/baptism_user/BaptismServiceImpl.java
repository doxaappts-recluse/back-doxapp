package pe.dcs.app.features.baptism_user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.dcs.app.features.baptism_user.service.BaptismService;

@Service
@RequiredArgsConstructor
public class BaptismServiceImpl implements BaptismService {

    /*private final UserRepository userRepository;
    private final BaptismRepository baptismRepository;
    private final AuthContext authContext;
    private final BaptismMapper baptismMapper;

    @Override
    @Transactional
    public BaptismDetailResponse create(
            BaptismRequest request
    ) {

        User target =
                userRepository.findById(
                                request.getUserId()
                        )
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Usuario no encontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (!authContext.getOrganizationId()
                .equals(target.getOrganization().getId())) {

            throw new Exceptions(
                    "El usuario no pertenece a su organización",
                    HttpStatus.FORBIDDEN
            );
        }

        if (baptismRepository.existsByUserId(target.getId())) {

            throw new Exceptions(
                    "El usuario ya tiene un bautismo registrado",
                    HttpStatus.BAD_REQUEST
            );
        }

        Baptism baptism = new Baptism();

        baptism.setUser(target);
        baptism.setBaptismDate(request.getBaptismDate());
        baptism.setChurchName(request.getChurchName());
        baptism.setPastorName(request.getPastorName());
        baptism.setCity(request.getCity());
        baptism.setVerified(Boolean.TRUE.equals(request.getVerified()));
        baptism.setObservations(request.getObservations());

        baptismRepository.save(baptism);

        return baptismMapper.detail(baptism);
    }

    @Override
    @Transactional
    public BaptismDetailResponse update(
            UUID id,
            BaptismRequest request
    ) {

        Baptism baptism =
                baptismRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Bautismo no encontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (!authContext.getOrganizationId()
                .equals(
                        baptism.getUser()
                                .getOrganization()
                                .getId()
                )) {

            throw new Exceptions(
                    "No tiene permisos sobre este registro",
                    HttpStatus.FORBIDDEN
            );
        }

        baptism.setBaptismDate(request.getBaptismDate());
        baptism.setChurchName(request.getChurchName());
        baptism.setPastorName(request.getPastorName());
        baptism.setCity(request.getCity());
        baptism.setVerified(Boolean.TRUE.equals(request.getVerified()));
        baptism.setObservations(request.getObservations());

        baptismRepository.save(baptism);

        return baptismMapper.detail(baptism);
    }

    @Override
    @Transactional(readOnly = true)
    public BaptismDetailResponse getById(UUID id) {

        Baptism baptism =
                baptismRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Bautismo no encontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (!authContext.getOrganizationId()
                .equals(
                        baptism.getUser()
                                .getOrganization()
                                .getId()
                )) {

            throw new Exceptions(
                    "No tiene permisos para visualizar este registro",
                    HttpStatus.FORBIDDEN
            );
        }

        return baptismMapper.detail(baptism);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BaptismSearchResponse> search(
            BaptismSearchRequest request
    ) {

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        BaptismFilter filters =
                request.getFilters();

        Specification<Baptism> spec =
                BaptismSpecification.filter(
                        authContext.getOrganizationId(),
                        filters != null ? filters.getName() : null,
                        filters != null ? filters.getLastname() : null,
                        filters != null ? filters.getVerified() : null
                );

        Page<Baptism> page =
                baptismRepository.findAll(
                        spec,
                        pageable
                );

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(baptismMapper::search)
                        .toList(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }*/
}
