package pe.dcs.app.features.event.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Event;
import pe.dcs.app.entity.EventRegistration;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.features.event.mapper.EventRegistrationMapper;
import pe.dcs.app.features.event.request.registration.EventPersonSearchRequest;
import pe.dcs.app.features.event.request.registration.EventRegistrationBulkRequest;
import pe.dcs.app.features.event.request.registration.EventRegistrationFilter;
import pe.dcs.app.features.event.request.registration.EventRegistrationRequest;
import pe.dcs.app.features.event.request.registration.EventRegistrationSearchRequest;
import pe.dcs.app.features.event.response.registration.EventPersonSearchResponse;
import pe.dcs.app.features.event.response.registration.EventRegistrationBulkErrorResponse;
import pe.dcs.app.features.event.response.registration.EventRegistrationBulkResponse;
import pe.dcs.app.features.event.response.registration.EventRegistrationDetailResponse;
import pe.dcs.app.features.event.response.registration.EventRegistrationResponse;
import pe.dcs.app.features.event.service.EventRegistrationService;
import pe.dcs.app.features.event.specification.EventPersonSpecification;
import pe.dcs.app.features.event.specification.EventRegistrationSpecification;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.EventAttendanceRepository;
import pe.dcs.app.repository.EventRegistrationRepository;
import pe.dcs.app.repository.EventRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.events.EventStatus;
import pe.dcs.app.util.enums.events.PaymentStatus;
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
    private final PersonRepository userRepository;
    private final EventAttendanceRepository attendanceRepository;
    private final BranchRepository branchRepository;
    private final EventRegistrationMapper eventRegistrationMapper;
    private final AuthContext authContext;
    private final EventAccessGuard eventAccessGuard;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventRegistrationResponse> search(
            EventRegistrationSearchRequest request
    ) {

        eventAccessGuard.assertCanUse();

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

        Event event =
                eventRepository.findById(
                        filter.getEventId()
                ).orElseThrow(() ->
                        new Exceptions(
                                "Evento no encontrado",
                                HttpStatus.NOT_FOUND
                        )
                );

        eventAccessGuard.assertCanAccess(event);

        /*
         * Quien no gestiona el evento (sede no coordinadora en un
         * evento compartido) solo puede ver las inscripciones que
         * su propia sede registró — igual criterio que
         * canManageRegistration, pero aplicado como filtro de
         * listado en vez de gate de escritura. Quien sí gestiona el
         * evento ve todas las sedes, y puede además filtrar por una
         * puntual si lo pide (filter.branchId).
         */
        if (!eventAccessGuard.canManage(event)) {
            filter.setBranchId(authContext.getCurrentBranchId());
        }

        Specification<EventRegistration> spec =
                EventRegistrationSpecification.filter(
                        filter,
                        authContext.getCurrentOrganizationId()
                );

        Page<EventRegistration> page =
                eventRegistrationRepository.findAll(
                        spec,
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(reg -> toResponse(reg, showAudit))
                        .toList(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    /**
     * Busca personas ya registradas en la organización para
     * inscribir como Miembro (exige membresía vigente y activa) o
     * Staff (cualquier persona registrada, con o sin membresía) —
     * ver EventPersonSpecification.
     *
     * Alcance: org admin puede buscar en TODA la organización, pero
     * SOLO si el evento es compartido (scope=ORGANIZATION); en
     * cualquier otro caso (branch admin, org user, o el propio org
     * admin en un evento de sede única) la búsqueda queda acotada a
     * la sede actual de quien busca. Un evento scope=BRANCH ya solo
     * es accesible por su sede coordinadora (assertCanAccess), así
     * que "sede actual" y "sede del evento" siempre coinciden ahí.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventPersonSearchResponse> searchPersons(
            EventPersonSearchRequest request
    ) {

        eventAccessGuard.assertCanUse();

        if (request.getCategory() != RegistrationCategory.MEMBER
                && request.getCategory() != RegistrationCategory.STAFF) {

            throw new Exceptions(
                    "Solo se puede buscar personas para las categorías Miembro o Staff",
                    HttpStatus.BAD_REQUEST
            );
        }

        Event event =
                eventRepository.findById(request.getEventId())
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Evento no encontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        eventAccessGuard.assertCanAccess(event);

        boolean orgWide =
                authContext.isCurrentOrganizationAdmin()
                        && event.isOrganizationScope();

        UUID branchId =
                orgWide ? null : authContext.getCurrentBranchId();

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        null
                );

        Specification<Person> spec =
                EventPersonSpecification.filter(
                        request,
                        authContext.getCurrentOrganizationId(),
                        branchId
                );

        Page<Person> page =
                userRepository.findAll(spec, pageable);

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(this::toPersonSearchResponse)
                        .toList(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    private EventPersonSearchResponse toPersonSearchResponse(Person person) {

        EventPersonSearchResponse response =
                new EventPersonSearchResponse();

        response.setId(person.getId());
        response.setName(person.getName());
        response.setLastname(person.getLastname());
        response.setPhone(person.getPhone());
        response.setDateBirth(person.getDateBirth());

        return response;
    }

    @Override
    public EventRegistrationResponse create(
            EventRegistrationRequest request
    ) {

        eventAccessGuard.assertCanUse();

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

        registration.setBranch(resolveCurrentBranch());

        registration.setPaymentStatus(
                resolvePaymentStatus(
                        registration.getFinalPrice(),
                        request.getPaymentStatus()
                )
        );

        return toResponse(
                eventRegistrationRepository.save(registration),
                authContext.canViewAudit()
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

        registration.setBranch(resolveCurrentBranch());

        registration.setPaymentStatus(
                resolvePaymentStatus(
                        registration.getFinalPrice(),
                        request.getPaymentStatus()
                )
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

        eventAccessGuard.assertCanUse();

        List<EventRegistrationResponse> responses =
                new ArrayList<>();

        List<EventRegistrationBulkErrorResponse> failed =
                new ArrayList<>();

        boolean showAudit = authContext.canViewAudit();

        List<EventRegistrationRequest> items =
                request.getRegistrations();

        /*
         * Un item inválido (p.ej. persona ya inscrita) ya no aborta
         * todo el lote: createRegistration() solo hace save() al
         * final, después de validar, así que si falla acá no queda
         * nada a medio guardar. Se captura por fila para poder
         * seguir con el resto y reportar cuáles fallaron y por qué.
         */
        for (int i = 0; i < items.size(); i++) {

            EventRegistrationRequest item = items.get(i);

            try {

                EventRegistration registration =
                        createRegistration(item);

                responses.add(toResponse(registration, showAudit));

            } catch (Exceptions e) {

                failed.add(
                        new EventRegistrationBulkErrorResponse(
                                i,
                                item.getName(),
                                item.getLastname(),
                                e.getMessage()
                        )
                );
            }
        }

        return new EventRegistrationBulkResponse(
                responses.size(),
                responses,
                failed.size(),
                failed
        );
    }

    @Override
    public EventRegistrationResponse update(
            UUID id,
            EventRegistrationRequest request
    ) {

        eventAccessGuard.assertCanUse();

        EventRegistration registration =
                findRegistration(id);

        eventAccessGuard.assertCanManageRegistration(registration);

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

        return toResponse(
                eventRegistrationRepository.save(registration),
                authContext.canViewAudit()
        );
    }

    @Override
    public void markPaid(UUID id) {

        eventAccessGuard.assertCanUse();

        EventRegistration registration =
                findRegistration(id);

        eventAccessGuard.assertCanManageRegistration(registration);

        if (registration.getStatus()
                == RegistrationStatus.CANCELLED) {

            throw new Exceptions(
                    "No se puede marcar como pagada una inscripción cancelada",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (registration.getPaymentStatus()
                == PaymentStatus.PAID) {

            throw new Exceptions(
                    "La inscripción ya se encuentra pagada",
                    HttpStatus.BAD_REQUEST
            );
        }

        registration.setPaymentStatus(PaymentStatus.PAID);

        eventRegistrationRepository.save(registration);
    }

    @Override
    public void cancel(UUID id) {

        eventAccessGuard.assertCanUse();

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

        eventAccessGuard.assertCanUse();

        return toDetailResponse(
                findRegistration(id)
        );
    }

    /**
     * Envuelve el mapper simple() agregando los campos derivados
     * del contexto de autorización (branchName, canManage) — mismo
     * patrón que EventFinanceServiceImpl.toResponse().
     */
    private EventRegistrationResponse toResponse(
            EventRegistration registration,
            boolean showAudit
    ) {

        EventRegistrationResponse response =
                eventRegistrationMapper.simple(registration, showAudit);

        response.setCanManage(
                eventAccessGuard.canManageRegistration(registration)
        );

        return response;
    }

    private EventRegistrationDetailResponse toDetailResponse(
            EventRegistration registration
    ) {

        EventRegistrationDetailResponse response =
                eventRegistrationMapper.detail(registration);

        response.setCanManage(
                eventAccessGuard.canManageRegistration(registration)
        );

        return response;
    }

    /**
     * Sede que registra la inscripción: la sede activa del
     * contexto actual. Puede quedar null si quien registra no
     * opera dentro de una sede puntual (p.ej. SYSTEM), en cuyo caso
     * solo quien gestiona el evento podrá administrar esa
     * inscripción.
     */
    private Branch resolveCurrentBranch() {

        UUID branchId = authContext.getCurrentBranchId();

        if (branchId == null) {
            return null;
        }

        return branchRepository.findById(branchId).orElse(null);
    }

    /**
     * Entrada gratuita (finalPrice == 0) SIEMPRE se considera
     * pagada, sin importar lo que se haya pedido — no tiene sentido
     * dejarla PENDING si no hay nada que cobrar. Si tiene costo, se
     * respeta lo pedido explícitamente (p.ej. cobro en efectivo al
     * momento de inscribir se puede marcar PAID de una vez); si no
     * se pidió nada, queda PENDING por defecto hasta que alguien la
     * marque pagada manualmente (ver markPaid()).
     */
    private PaymentStatus resolvePaymentStatus(
            BigDecimal finalPrice,
            PaymentStatus requested
    ) {

        boolean isFree =
                finalPrice == null
                        || finalPrice.compareTo(BigDecimal.ZERO) == 0;

        if (isFree) {
            return PaymentStatus.PAID;
        }

        return requested != null
                ? requested
                : PaymentStatus.PENDING;
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

        eventAccessGuard.assertCanAccess(registration.getEvent());

        return registration;
    }

    private void generateQrToken(EventRegistration registration) {
        registration.setQrToken(UUID.randomUUID().toString());
    }

    private void validateEvent(
            Event event
    ) {

        eventAccessGuard.assertCanAccess(event);

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

        Person user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new Exceptions(
                                "Usuario no encontrado",
                                HttpStatus.NOT_FOUND
                        )
                );

        PersonBranch activeBranch =
                user.getBranchHistory()
                        .stream()
                        .filter(pb -> pb.getStatus() == StatusType.ACTIVE)
                        .findFirst()
                        .orElseThrow(() ->
                                new Exceptions(
                                        "El usuario no tiene una sede activa",
                                        HttpStatus.CONFLICT
                                )
                        );

        if (!activeBranch.getBranch()
                .getOrganization()
                .getId()
                .equals(authContext.getCurrentOrganizationId())) {

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
