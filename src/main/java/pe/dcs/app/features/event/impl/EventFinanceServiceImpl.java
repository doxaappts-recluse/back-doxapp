package pe.dcs.app.features.event.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Event;
import pe.dcs.app.entity.EventFinance;
import pe.dcs.app.entity.Person;
import pe.dcs.app.features.event.mapper.EventFinanceMapper;
import pe.dcs.app.features.event.request.finance.*;
import pe.dcs.app.features.event.response.finance.EventFinanceResponse;
import pe.dcs.app.features.event.service.EventFinanceService;
import pe.dcs.app.features.event.specification.EventFinanceSpecification;
import pe.dcs.app.repository.EventFinanceRepository;
import pe.dcs.app.repository.EventRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.events.EventFinanceStatus;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventFinanceServiceImpl
        implements EventFinanceService {

    private final EventFinanceRepository eventFinanceRepository;
    private final EventRepository eventRepository;
    private final EventFinanceMapper eventFinanceMapper;
    private final AuthContext authContext;
    private final PersonRepository userRepository;
    private final EventAccessGuard eventAccessGuard;

    /**
     * Valida que el evento pertenezca a la organización del
     * contexto actual, y que quien llama tenga acceso a él (tier
     * amplio) — delega en EventAccessGuard, misma regla que
     * EventServiceImpl/EventRegistrationServiceImpl.
     */
    private void validateEventOrg(Event event) {
        eventAccessGuard.assertCanAccess(event);
    }

    /**
     * simple() + canManage/owner calculados — el front los usa para
     * mostrar editar/aprobar/rechazar solo a quien corresponde:
     * canManage (org admin, sede coordinadora, u org user creador
     * del EVENTO con EDIT) habilita aprobar/rechazar; canManage U
     * owner (creador de ESTE movimiento puntual) habilita editar.
     * Deliberadamente NO se le da a `owner` autoridad de aprobación
     * — ver doc en EventFinanceResponse.
     */
    private EventFinanceResponse toResponse(EventFinance finance, boolean showAudit) {

        EventFinanceResponse response =
                eventFinanceMapper.simple(finance, showAudit);

        response.setCanManage(
                eventAccessGuard.canManage(finance.getEvent())
        );

        UUID currentUserId = authContext.getUserId();

        response.setOwner(
                finance.getCreatedByUser() != null
                        && currentUserId != null
                        && finance.getCreatedByUser().getId().equals(currentUserId)
        );

        return response;
    }

    @Override
    @Transactional
    public EventFinanceResponse create(
            EventFinanceRequest request
    ) {

        Event event =
                eventRepository.findById(
                                request.getEventId()
                        )
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.eventoNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        validateEventOrg(event);

        Person user =
                userRepository.findById(
                                authContext.getUserId()
                        )
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.usuarioNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        EventFinance finance =
                new EventFinance();

        finance.setEvent(event);

        finance.setCreatedByUser(
                user
        );

        finance.setType(
                request.getType()
        );

        finance.setDescription(
                request.getDescription()
        );

        finance.setAmount(
                request.getAmount()
        );

        finance.setTransactionDate(
                request.getTransactionDate()
        );

        finance.setObservations(
                request.getObservations()
        );

        if (eventAccessGuard.canManage(event)) {

            finance.setStatus(
                    EventFinanceStatus.APPROVED
            );

            finance.setApprovedByUser(
                    user
            );

            finance.setApprovedAt(
                    Instant.now()
            );

        } else {

            finance.setStatus(
                    EventFinanceStatus.PENDING
            );
        }

        return toResponse(
                eventFinanceRepository.save(finance),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional
    public EventFinanceResponse update(
            UUID id,
            EventFinanceRequest request
    ) {

        EventFinance finance =
                eventFinanceRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.movimientoFinancieroNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );


        if (finance.getStatus() ==
                EventFinanceStatus.APPROVED) {

            throw new Exceptions(
                    "error.noPuedeEditarMovimientoAprobado",
                    HttpStatus.BAD_REQUEST
            );
        }


        if (!eventAccessGuard.canManage(finance.getEvent())) {

            UUID ownerId =
                    finance.getCreatedByUser()
                            .getId();

            if (!ownerId.equals(
                    authContext.getUserId()
            )) {

                throw new Exceptions(
                        "error.noPuedeEditarMovimientosOtrosUsuarios",
                        HttpStatus.FORBIDDEN
                );
            }
        }

        Event event =
                eventRepository.findById(
                                request.getEventId()
                        )
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.eventoNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        validateEventOrg(event);

        finance.setEvent(event);

        finance.setType(
                request.getType()
        );

        finance.setDescription(
                request.getDescription()
        );

        finance.setAmount(
                request.getAmount()
        );

        finance.setTransactionDate(
                request.getTransactionDate()
        );

        finance.setObservations(
                request.getObservations()
        );


        if (finance.getStatus() ==
                EventFinanceStatus.REJECTED) {

            finance.setStatus(
                    EventFinanceStatus.PENDING
            );

            finance.setApprovedByUser(
                    null
            );

            finance.setApprovedAt(
                    null
            );
        }

        finance.setUpdatedAt(
                Instant.now()
        );

        return toResponse(
                eventFinanceRepository.save(finance),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional
    public EventFinanceResponse approve(
            UUID id,
            EventFinanceApproveRequest request
    ) {

        EventFinance finance =
                eventFinanceRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.movimientoFinancieroNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (!eventAccessGuard.canManage(finance.getEvent())) {

            throw new Exceptions(
                    "error.noTienePermisosAprobarMovimientosEvento",
                    HttpStatus.FORBIDDEN
            );
        }

        if (finance.getStatus() !=
                EventFinanceStatus.PENDING) {

            throw new Exceptions(
                    "error.soloPuedenAprobarMovimientosPendientes",
                    HttpStatus.BAD_REQUEST
            );
        }

        Person admin =
                userRepository.findById(
                                authContext.getUserId()
                        )
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.usuarioNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        finance.setStatus(
                EventFinanceStatus.APPROVED
        );

        finance.setApprovedByUser(
                admin
        );

        finance.setApprovedAt(
                Instant.now()
        );

        if (request != null &&
                request.getObservations() != null) {

            finance.setObservations(
                    request.getObservations()
            );
        }

        return toResponse(
                eventFinanceRepository.save(finance),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional
    public EventFinanceResponse reject(
            UUID id,
            EventFinanceRejectRequest request
    ) {

        EventFinance finance =
                eventFinanceRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.movimientoFinancieroNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (!eventAccessGuard.canManage(finance.getEvent())) {

            throw new Exceptions(
                    "error.noTienePermisosAprobarMovimientosEvento",
                    HttpStatus.FORBIDDEN
            );
        }

        if (finance.getStatus() !=
                EventFinanceStatus.PENDING) {

            throw new Exceptions(
                    "error.soloPuedenRechazarMovimientosPendientes",
                    HttpStatus.BAD_REQUEST
            );
        }

        finance.setStatus(
                EventFinanceStatus.REJECTED
        );

        finance.setObservations(
                request.getReason()
        );

        finance.setUpdatedAt(
                Instant.now()
        );

        return toResponse(
                eventFinanceRepository.save(finance),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public EventFinanceResponse getById(
            UUID id
    ) {

        EventFinance finance =
                eventFinanceRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.movimientoFinancieroNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        validateEventOrg(finance.getEvent());

        return toResponse(
                finance,
                authContext.canViewAudit()
        );
    }

    @Override
    public PageResponse<EventFinanceResponse> search(
            EventFinanceSearchRequest request
    ) {

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        EventFinanceFilter filters =
                request.getFilters();

        if (filters == null || filters.getEventId() == null) {

            throw new Exceptions(
                    "error.elEventoEsRequerido",
                    HttpStatus.BAD_REQUEST
            );
        }

        Event event =
                eventRepository.findById(
                                filters.getEventId()
                        )
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.eventoNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        validateEventOrg(event);

        Specification<EventFinance> spec =
                EventFinanceSpecification.filter(
                        filters != null
                                ? filters.getEventId()
                                : null,
                        filters != null
                                ? filters.getType()
                                : null,
                        filters != null
                                ? filters.getStatus()
                                : null,
                        filters != null
                                ? filters.getStartDate()
                                : null,
                        filters != null
                                ? filters.getEndDate()
                                : null
                );

        Page<EventFinance> page =
                eventFinanceRepository.findAll(
                        spec,
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(f -> toResponse(f, showAudit))
                        .toList(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }
}