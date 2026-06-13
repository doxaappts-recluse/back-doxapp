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
import pe.dcs.app.entity.User;
import pe.dcs.app.features.event.mapper.EventFinanceMapper;
import pe.dcs.app.features.event.request.finance.*;
import pe.dcs.app.features.event.response.finance.EventFinanceResponse;
import pe.dcs.app.features.event.service.EventFinanceService;
import pe.dcs.app.features.event.specification.EventFinanceSpecification;
import pe.dcs.app.repository.EventFinanceRepository;
import pe.dcs.app.repository.EventRepository;
import pe.dcs.app.repository.UserRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.events.EventFinanceStatus;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventFinanceServiceImpl
        implements EventFinanceService {

    private final EventFinanceRepository eventFinanceRepository;
    private final EventRepository eventRepository;
    private final EventFinanceMapper eventFinanceMapper;
    private final AuthContext authContext;
    private final UserRepository userRepository;

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
                                        "Evento no encontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        User user =
                userRepository.findById(
                                authContext.getUserId()
                        )
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Usuario no encontrado",
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

        if (authContext.isOrgAdmin()) {

            finance.setStatus(
                    EventFinanceStatus.APPROVED
            );

            finance.setApprovedByUser(
                    user
            );

            finance.setApprovedAt(
                    LocalDateTime.now()
            );

        } else {

            finance.setStatus(
                    EventFinanceStatus.PENDING
            );
        }

        return eventFinanceMapper.simple(
                eventFinanceRepository.save(finance)
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
                                        "Movimiento financiero no encontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        /*
         * Los movimientos aprobados son inmutables.
         */
        if (finance.getStatus() ==
                EventFinanceStatus.APPROVED) {

            throw new Exceptions(
                    "No se puede editar un movimiento aprobado",
                    HttpStatus.BAD_REQUEST
            );
        }

        /*
         * ORG_USER solamente puede editar
         * movimientos creados por él mismo.
         */
        if (authContext.isOrgUser()) {

            UUID ownerId =
                    finance.getCreatedByUser()
                            .getId();

            if (!ownerId.equals(
                    authContext.getUserId()
            )) {

                throw new Exceptions(
                        "No puede editar movimientos de otros usuarios",
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
                                        "Evento no encontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

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

        /*
         * Si estaba rechazado vuelve
         * al flujo de aprobación.
         */
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
                LocalDateTime.now()
        );

        return eventFinanceMapper.simple(
                eventFinanceRepository.save(finance)
        );
    }

    @Override
    @Transactional
    public EventFinanceResponse approve(
            UUID id,
            EventFinanceApproveRequest request
    ) {

        if (!authContext.isOrgAdmin()) {

            throw new Exceptions(
                    "No tiene permisos para aprobar movimientos",
                    HttpStatus.FORBIDDEN
            );
        }

        EventFinance finance =
                eventFinanceRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Movimiento financiero no encontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (finance.getStatus() !=
                EventFinanceStatus.PENDING) {

            throw new Exceptions(
                    "Solo se pueden aprobar movimientos pendientes",
                    HttpStatus.BAD_REQUEST
            );
        }

        User admin =
                userRepository.findById(
                                authContext.getUserId()
                        )
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Usuario no encontrado",
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
                LocalDateTime.now()
        );

        if (request != null &&
                request.getObservations() != null) {

            finance.setObservations(
                    request.getObservations()
            );
        }

        return eventFinanceMapper.simple(
                eventFinanceRepository.save(finance)
        );
    }

    @Override
    @Transactional
    public EventFinanceResponse reject(
            UUID id,
            EventFinanceRejectRequest request
    ) {

        if (!authContext.isOrgAdmin()) {

            throw new Exceptions(
                    "No tiene permisos para rechazar movimientos",
                    HttpStatus.FORBIDDEN
            );
        }

        EventFinance finance =
                eventFinanceRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Movimiento financiero no encontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (finance.getStatus() !=
                EventFinanceStatus.PENDING) {

            throw new Exceptions(
                    "Solo se pueden rechazar movimientos pendientes",
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
                LocalDateTime.now()
        );

        return eventFinanceMapper.simple(
                eventFinanceRepository.save(finance)
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
                                        "Movimiento financiero no encontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        return eventFinanceMapper.simple(
                finance
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

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(eventFinanceMapper::simple)
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