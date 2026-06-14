package pe.dcs.app.features.event.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Event;
import pe.dcs.app.entity.EventRegistration;
import pe.dcs.app.entity.User;
import pe.dcs.app.features.event.mapper.EventRegistrationMapper;
import pe.dcs.app.features.event.request.registration.EventRegistrationBulkRequest;
import pe.dcs.app.features.event.request.registration.EventRegistrationFilter;
import pe.dcs.app.features.event.request.registration.EventRegistrationRequest;
import pe.dcs.app.features.event.request.registration.EventRegistrationSearchRequest;
import pe.dcs.app.features.event.response.registration.EventRegistrationBulkResponse;
import pe.dcs.app.features.event.response.registration.EventRegistrationDetailResponse;
import pe.dcs.app.features.event.response.registration.EventRegistrationResponse;
import pe.dcs.app.features.event.service.EventRegistrationService;
import pe.dcs.app.features.event.specification.EventRegistrationSpecification;
import pe.dcs.app.repository.EventAttendanceRepository;
import pe.dcs.app.repository.EventRegistrationRepository;
import pe.dcs.app.repository.EventRepository;
import pe.dcs.app.repository.UserRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.events.EventStatus;
import pe.dcs.app.util.enums.events.RegistrationCategory;
import pe.dcs.app.util.enums.events.RegistrationStatus;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Transactional
public class EventRegistrationServiceImpl implements EventRegistrationService {

    private final EventRegistrationRepository eventRegistrationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EventAttendanceRepository attendanceRepository;
    private final EventRegistrationMapper eventRegistrationMapper;
    private final AuthContext authContext;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventRegistrationResponse> search(
            EventRegistrationSearchRequest request
    ) {

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        EventRegistrationFilter filter =
                request.getFilters();

        if (request.getFilters().getEventId() == null) {
            throw new Exceptions(
                    "El evento es requerido",
                    HttpStatus.BAD_REQUEST
            );
        }

        Specification<EventRegistration> spec =
                EventRegistrationSpecification.filter(
                        filter,
                        authContext.getOrganizationId()
                );

        Page<EventRegistration> page =
                eventRegistrationRepository.findAll(
                        spec,
                        pageable
                );

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(eventRegistrationMapper::simple)
                        .toList(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    @Override
    public EventRegistrationResponse create(
            EventRegistrationRequest request
    ) {

        Event event = eventRepository.findById(
                request.getEventId()
        ).orElseThrow(() ->
                new Exceptions(
                        "Evento no encontrado",
                        HttpStatus.NOT_FOUND
                )
        );

        validateEvent(event);

        validateCapacity(event);

        EventRegistration registration = new EventRegistration();

        registration.setEvent(event);
        registration.setCategory(request.getCategory());

        populateParticipantData(
                registration,
                request
        );

        generateQrToken(registration);

        BigDecimal discount = normalizeDiscount(
                request.getDiscount()
        );

        validateDiscount(
                event.getPrice(),
                discount
        );

        registration.setRegularPrice(event.getPrice());
        registration.setDiscount(discount);

        registration.setFinalPrice(
                event.getPrice().subtract(discount)
        );

        registration.setBirthDate(request.getBirthDate());

        registration.setStatus(
                RegistrationStatus.REGISTERED
        );

        registration.setObservations(
                request.getObservations()
        );

        return eventRegistrationMapper.simple(
                eventRegistrationRepository.save(registration)
        );
    }

    private EventRegistration createRegistration(
            EventRegistrationRequest request
    ) {

        Event event = eventRepository.findById(
                request.getEventId()
        ).orElseThrow(() ->
                new Exceptions(
                        "Evento no encontrado",
                        HttpStatus.NOT_FOUND
                )
        );

        validateEvent(event);

        //validateCapacity(event);

        EventRegistration registration =
                new EventRegistration();

        registration.setEvent(event);

        registration.setCategory(
                request.getCategory()
        );

        populateParticipantData(
                registration,
                request
        );

        generateQrToken(registration);

        BigDecimal discount =
                normalizeDiscount(
                        request.getDiscount()
                );

        validateDiscount(
                event.getPrice(),
                discount
        );

        registration.setRegularPrice(
                event.getPrice()
        );

        registration.setBirthDate(request.getBirthDate());

        registration.setDiscount(
                discount
        );

        registration.setFinalPrice(
                event.getPrice()
                        .subtract(discount)
        );

        registration.setStatus(
                RegistrationStatus.REGISTERED
        );

        registration.setObservations(
                request.getObservations()
        );

        return eventRegistrationRepository.save(
                registration
        );
    }

    @Override
    @Transactional
    public EventRegistrationBulkResponse bulkCreate(
            EventRegistrationBulkRequest request
    ) {

        List<EventRegistrationResponse> responses =
                new ArrayList<>();

        for (EventRegistrationRequest item :
                request.getRegistrations()) {

            EventRegistration registration =
                    createRegistration(item);

            responses.add(eventRegistrationMapper.simple(registration));
        }

        return new EventRegistrationBulkResponse(
                responses.size(),
                responses
        );
    }

    @Override
    public EventRegistrationResponse update(
            UUID id,
            EventRegistrationRequest request
    ) {

        EventRegistration registration =
                findRegistration(id);

        if (registration.getStatus()
                == RegistrationStatus.CANCELLED) {

            throw new Exceptions(
                    "No se puede editar una inscripción cancelada",
                    HttpStatus.BAD_REQUEST
            );
        }

        validateAttendance(id);

        BigDecimal discount = normalizeDiscount(
                request.getDiscount()
        );

        validateDiscount(
                registration.getRegularPrice(),
                discount
        );

        if (isExternalCategory(
                registration.getCategory()
        )) {

            registration.setName(request.getName());
            registration.setLastname(request.getLastname());
            registration.setPhone(request.getPhone());
            registration.setEmail(request.getEmail());
        }

        registration.setDiscount(discount);

        registration.setFinalPrice(
                registration.getRegularPrice()
                        .subtract(discount)
        );

        registration.setBirthDate(request.getBirthDate());

        registration.setObservations(
                request.getObservations()
        );

        return eventRegistrationMapper.simple(
                eventRegistrationRepository.save(registration)
        );
    }

    @Override
    public void cancel(UUID id) {

        EventRegistration registration =
                findRegistration(id);

        validateAttendance(id);

        if (registration.getStatus()
                == RegistrationStatus.CANCELLED) {

            throw new Exceptions(
                    "La inscripción ya se encuentra cancelada",
                    HttpStatus.BAD_REQUEST
            );
        }

        registration.setStatus(
                RegistrationStatus.CANCELLED
        );

        eventRegistrationRepository.save(
                registration
        );
    }

    @Override
    @Transactional(readOnly = true)
    public EventRegistrationDetailResponse getById(
            UUID id
    ) {

        return eventRegistrationMapper.detail(
                findRegistration(id)
        );
    }

    private EventRegistration findRegistration(
            UUID id
    ) {

        EventRegistration registration =
                eventRegistrationRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Inscripción no encontrada",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (!registration.getEvent()
                .getOrganization()
                .getId()
                .equals(authContext.getOrganizationId())) {

            throw new Exceptions(
                    "No tiene acceso a esta inscripción",
                    HttpStatus.FORBIDDEN
            );
        }

        return registration;
    }

    private void generateQrToken(EventRegistration registration) {
        registration.setQrToken(UUID.randomUUID().toString());
    }

    private void validateEvent(
            Event event
    ) {

        if (!event.getOrganization()
                .getId()
                .equals(authContext.getOrganizationId())) {

            throw new Exceptions(
                    "El evento no pertenece a la organización",
                    HttpStatus.FORBIDDEN
            );
        }

        if (event.getStatus()
                == EventStatus.CANCELLED) {

            throw new Exceptions(
                    "El evento fue cancelado",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (event.getStatus()
                == EventStatus.FINISHED) {

            throw new Exceptions(
                    "El evento ya finalizó",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (event.getStatus()
                != EventStatus.PUBLISHED) {

            throw new Exceptions(
                    "Solo se permiten inscripciones en eventos publicados",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validateCapacity(
            Event event
    ) {

        long current =
                eventRegistrationRepository
                        .countByEventIdAndStatus(
                                event.getId(),
                                RegistrationStatus.REGISTERED
                        );

        if (current >= event.getCapacity()) {

            throw new Exceptions(
                    "El evento alcanzó su capacidad máxima",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validateAttendance(
            UUID registrationId
    ) {

        if (attendanceRepository
                .existsByRegistration_Id(
                        registrationId
                )) {

            throw new Exceptions(
                    "La inscripción ya registra asistencia",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void populateParticipantData(
            EventRegistration registration,
            EventRegistrationRequest request
    ) {

        if (isInternalCategory(
                request.getCategory()
        )) {

            createInternalParticipant(
                    registration,
                    request.getUserId()
            );

            return;
        }

        createExternalParticipant(
                registration,
                request
        );
    }

    private void createInternalParticipant(
            EventRegistration registration,
            UUID userId
    ) {

        if (userId == null) {

            throw new Exceptions(
                    "Debe seleccionar un miembro",
                    HttpStatus.BAD_REQUEST
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new Exceptions(
                                "Usuario no encontrado",
                                HttpStatus.NOT_FOUND
                        )
                );

        if (!user.getOrganization()
                .getId()
                .equals(authContext.getOrganizationId())) {

            throw new Exceptions(
                    "El usuario no pertenece a la organización",
                    HttpStatus.BAD_REQUEST
            );
        }

        boolean existsActive =
                eventRegistrationRepository
                        .existsByEventIdAndUserIdAndStatusNot(
                                registration.getEvent().getId(),
                                userId,
                                RegistrationStatus.CANCELLED
                        );

        if (existsActive) {
            throw new Exceptions(
                    "El usuario ya se encuentra inscrito",
                    HttpStatus.BAD_REQUEST
            );
        }

        registration.setUser(user);

        registration.setName(
                user.getName()
        );

        registration.setLastname(
                user.getLastname()
        );

        registration.setPhone(
                user.getPhone()
        );
    }

    private void createExternalParticipant(
            EventRegistration registration,
            EventRegistrationRequest request
    ) {

        if (request.getName() == null ||
                request.getName().isBlank()) {

            throw new Exceptions(
                    "El nombre es obligatorio",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.getLastname() == null ||
                request.getLastname().isBlank()) {

            throw new Exceptions(
                    "El apellido es obligatorio",
                    HttpStatus.BAD_REQUEST
            );
        }

        registration.setName(
                request.getName()
        );

        registration.setLastname(
                request.getLastname()
        );

        registration.setPhone(
                request.getPhone()
        );

        registration.setEmail(
                request.getEmail()
        );
    }

    private boolean isInternalCategory(
            RegistrationCategory category
    ) {

        return category == RegistrationCategory.MEMBER
                || category == RegistrationCategory.STAFF;
    }

    private boolean isExternalCategory(
            RegistrationCategory category
    ) {

        return category == RegistrationCategory.VISITOR
                || category == RegistrationCategory.GUEST
                || category == RegistrationCategory.TEMP_MEMBER
                || category == RegistrationCategory.TEMP_STAFF
                || category == RegistrationCategory.SCHOLARSHIP;
    }

    private BigDecimal normalizeDiscount(
            BigDecimal discount
    ) {

        return discount == null
                ? BigDecimal.ZERO
                : discount;
    }

    private void validateDiscount(
            BigDecimal price,
            BigDecimal discount
    ) {

        if (discount.compareTo(BigDecimal.ZERO) < 0) {

            throw new Exceptions(
                    "El descuento no puede ser negativo",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (discount.compareTo(price) > 0) {

            throw new Exceptions(
                    "El descuento no puede ser mayor al precio",
                    HttpStatus.BAD_REQUEST
            );
        }
    }
}
