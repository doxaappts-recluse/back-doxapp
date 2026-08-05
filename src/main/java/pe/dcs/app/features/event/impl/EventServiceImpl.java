package pe.dcs.app.features.event.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Event;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.features.event.mapper.EventMapper;
import pe.dcs.app.features.event.request.event.EventFilter;
import pe.dcs.app.features.event.request.event.EventRequest;
import pe.dcs.app.features.event.request.event.EventSearchRequest;
import pe.dcs.app.features.event.response.event.EventDetailResponse;
import pe.dcs.app.features.event.response.event.EventResponse;
import pe.dcs.app.features.event.service.EventService;
import pe.dcs.app.features.event.specification.EventSpecification;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.EventRepository;
import pe.dcs.app.repository.OrganizationRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.service.supabase.SupabaseStorageService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.events.EventScope;
import pe.dcs.app.util.enums.events.EventStatus;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final AuthContext authContext;
    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final OrganizationRepository organizationRepository;
    private final BranchRepository branchRepository;
    private final SupabaseStorageService storageService;
    private final EventAccessGuard eventAccessGuard;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> search(
            EventSearchRequest request
    ) {

        eventAccessGuard.assertCanUse();

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        EventFilter filters =
                request.getFilters();

        /*
         * Cualquiera que no sea org admin (branch admin U org user
         * delegado) queda acotado: solo ve eventos ORGANIZATION
         * (compartidos) o BRANCH de su propia sede. Antes esto solo
         * se activaba para branch admin; un org user delegado
         * quedaba viendo TODO por accidente (restrictToBranch daba
         * false porque isCurrentBranchAdmin() es false para él).
         */
        boolean restrictToBranch =
                !authContext.isCurrentOrganizationAdmin();

        Specification<Event> spec =
                EventSpecification.filter(
                        authContext.getCurrentOrganizationId(),
                        authContext.getCurrentBranchId(),
                        restrictToBranch,
                        filters != null
                                ? filters.getName()
                                : null,
                        filters != null
                                ? filters.getStatus()
                                : null,
                        filters != null
                                ? filters.getStartDateFrom()
                                : null,
                        filters != null
                                ? filters.getStartDateTo()
                                : null
                );

        Page<Event> page =
                eventRepository.findAll(
                        spec,
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(event -> {
                            EventResponse response =
                                    eventMapper.simple(event, showAudit);
                            response.setCanManage(eventAccessGuard.canManage(event));
                            return response;
                        })
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
    @Transactional
    public EventDetailResponse create(
            EventRequest request,
            MultipartFile file
    ) {

        eventAccessGuard.assertCanCreate();

        validateDates(request);

        Organization organization =
                organizationRepository.findById(
                        authContext.getCurrentOrganizationId()
                ).orElseThrow();

        Event event = new Event();
        System.out.println("DB VALUE: " + request.getStartDateTime());
        event.setName(request.getName());
        event.setDescription(request.getDescription());
        event.setStartDateTime(request.getStartDateTime());
        event.setEndDateTime(request.getEndDateTime());
        event.setLocation(request.getLocation());
        event.setPrice(request.getPrice());
        event.setCapacity(request.getCapacity());
        event.setGoal(request.getGoal());
        event.setExpectedBudget(request.getExpectedBudget());
        event.setStatus(EventStatus.DRAFT);
        event.setOrganization(organization);
        event.setTemplateConfig(request.getTemplateConfig());
        applyScopeAndBranch(event, request);
        System.out.println("DB2 VALUE: " + event.getStartDateTime());

        // 1. persistir primero para obtener ID
        Event saved = eventRepository.save(event);

        // 2. upload opcional
        if (file != null && !file.isEmpty()) {

            String path = buildPath(
                    organization.getId(),
                    saved.getId()
            );

            try (InputStream input = file.getInputStream()) {

                storageService.upload(
                        input,
                        "events",
                        path,
                        file.getContentType()
                );

                // 3. actualizar entity (dirty checking)
                saved.setTemplatePath(path);

            } catch (IOException e) {
                throw new RuntimeException("Error uploading event image", e);
            }
        }

        return toDetail(saved);
    }

    @Override
    @Transactional
    public EventDetailResponse update(
            UUID id,
            EventRequest request,
            MultipartFile file
    ) {

        validateDates(request);

        Event event = findForManage(id);

        event.setName(request.getName());
        event.setDescription(request.getDescription());
        event.setStartDateTime(request.getStartDateTime());
        event.setEndDateTime(request.getEndDateTime());
        event.setLocation(request.getLocation());
        event.setPrice(request.getPrice());
        event.setCapacity(request.getCapacity());
        event.setGoal(request.getGoal());
        event.setExpectedBudget(request.getExpectedBudget());
        event.setTemplateConfig(request.getTemplateConfig());
        applyScopeAndBranch(event, request);

        // 1. upload opcional (overwrite)
        if (file != null && !file.isEmpty()) {

            String path = buildPath(
                    event.getOrganization().getId(),
                    event.getId()
            );

            try (InputStream input = file.getInputStream()) {

                storageService.upload(
                        input,
                        "events",
                        path,
                        file.getContentType()
                );

                // 2. actualizar path (overwrite lógico)
                event.setTemplatePath(path);

            } catch (IOException e) {
                throw new RuntimeException("Error updating event image", e);
            }
        }

        Event saved = eventRepository.save(event);

        return toDetail(saved);
    }

    private String buildPath(UUID orgId, UUID eventId) {
        return orgId + "/" + eventId + "/template.png";
    }

    /**
     * detail() + canManage calculado — así el front sabe, apenas
     * crea/edita/publica/cancela/consulta un evento, si puede
     * entrar al dashboard/reportes/asistencia o solo a
     * inscripciones/finanzas (tier amplio).
     */
    private EventDetailResponse toDetail(Event event) {
        EventDetailResponse response = eventMapper.detail(event);
        response.setCanManage(eventAccessGuard.canManage(event));
        return response;
    }

    /**
     * Decide sede coordinadora + scope del evento. La sede
     * SIEMPRE queda asignada, sin importar el scope elegido:
     * cualquiera que no sea org admin (branch admin U org user
     * delegado) queda ligado automáticamente a su propia sede; un
     * org admin debe elegirla explícitamente siempre (incluso si
     * el evento va a ser visible a toda la organización). El scope
     * es un eje independiente que solo decide si OTRAS sedes
     * también pueden ver el evento e inscribir/aportar finanzas —
     * quién GESTIONA el evento lo decide EventAccessGuard.canManage.
     */
    private void applyScopeAndBranch(Event event, EventRequest request) {

        EventScope scope =
                request.getScope() != null
                        ? request.getScope()
                        : EventScope.ORGANIZATION;

        event.setScope(scope);

        /*
         * Cualquiera que no sea org admin (branch admin U org user
         * delegado) queda ligado a su propia sede automáticamente,
         * sin poder elegir otra — solo el org admin puede asignar
         * la sede coordinadora libremente.
         */
        if (!authContext.isCurrentOrganizationAdmin()) {

            Branch ownBranch =
                    branchRepository.findById(
                            authContext.getCurrentBranchId()
                    ).orElseThrow(() ->
                            new Exceptions(
                                    "error.sedeNoEncontrada",
                                    HttpStatus.NOT_FOUND
                            )
                    );

            event.setBranch(ownBranch);
            return;
        }

        if (request.getBranchId() == null) {
            throw new Exceptions(
                    "error.debeSeleccionarSedeCoordinadoraEvento",
                    HttpStatus.BAD_REQUEST
            );
        }

        Branch branch =
                branchRepository.findById(
                        request.getBranchId()
                ).orElseThrow(() ->
                        new Exceptions(
                                "error.sedeNoEncontrada",
                                HttpStatus.NOT_FOUND
                        )
                );

        if (!branch.getOrganization()
                .getId()
                .equals(authContext.getCurrentOrganizationId())) {

            throw new Exceptions(
                    "error.sedeNoPerteneceOrganizacion",
                    HttpStatus.BAD_REQUEST
            );
        }

        event.setBranch(branch);
    }

    @Override
    @Transactional(readOnly = true)
    public EventDetailResponse getById(
            UUID id
    ) {

        eventAccessGuard.assertCanUse();

        Event event =
                findAndValidate(id);

        return toDetail(event);
    }

    /**
     * Tier amplio (visibilidad): ver el evento, inscribir gente,
     * aportar finanzas. Delega en EventAccessGuard.canAccess.
     */
    private Event findAndValidate(
            UUID id
    ) {

        Event event =
                eventRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.eventoNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        eventAccessGuard.assertCanAccess(event);

        return event;
    }

    /**
     * Tier angosto (gestión): editar/publicar/cancelar. Delega en
     * EventAccessGuard.canManage — que un evento sea visible a
     * toda la organización no habilita a otras sedes u otros
     * usuarios a editarlo.
     */
    private Event findForManage(
            UUID id
    ) {
        return eventAccessGuard.assertCanManage(id);
    }

    @Override
    @Transactional
    public EventDetailResponse publish(
            UUID id
    ) {

        Event event =
                findForManage(id);

        if (event.getStatus() == EventStatus.CANCELLED) {

            throw new Exceptions(
                    "error.noPuedePublicarEventoCancelado",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (event.getStatus() == EventStatus.FINISHED) {

            throw new Exceptions(
                    "error.noPuedePublicarEventoFinalizado",
                    HttpStatus.BAD_REQUEST
            );
        }

        event.setStatus(
                EventStatus.PUBLISHED
        );

        eventRepository.save(event);

        return toDetail(event);
    }

    @Override
    @Transactional
    public EventDetailResponse cancel(
            UUID id
    ) {

        Event event =
                findForManage(id);

        if (event.getStatus() == EventStatus.FINISHED) {

            throw new Exceptions(
                    "error.noPuedeCancelarEventoFinalizado",
                    HttpStatus.BAD_REQUEST
            );
        }

        event.setStatus(
                EventStatus.CANCELLED
        );

        eventRepository.save(event);

        return toDetail(event);
    }

    @Override
    public Event findById(UUID id) {

        return eventRepository
                .findByIdAndOrganization_Id(id, authContext.getCurrentOrganizationId())
                .orElseThrow(() -> new RuntimeException("Event not found"));
    }

    private void validateDates(
            EventRequest request
    ) {

        if (
                request.getEndDateTime()
                        .isBefore(
                                request.getStartDateTime()
                        )
        ) {

            throw new Exceptions(
                    "error.fechaFinDebeSerMayorFecha",
                    HttpStatus.BAD_REQUEST
            );
        }
    }
}
