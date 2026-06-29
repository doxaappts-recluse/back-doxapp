package pe.dcs.app.features.event.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
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
import pe.dcs.app.repository.EventRepository;
import pe.dcs.app.repository.OrganizationRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.service.supabase.SupabaseStorageService;
import pe.dcs.app.util.Exceptions;
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
    private final SupabaseStorageService storageService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> search(
            EventSearchRequest request
    ) {

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        EventFilter filters =
                request.getFilters();

        Specification<Event> spec =
                EventSpecification.filter(
                        authContext.getOrganizationId(),
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

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(eventMapper::simple)
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

        validateDates(request);

        Organization organization =
                organizationRepository.findById(
                        authContext.getOrganizationId()
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

        return eventMapper.detail(saved);
    }

    @Override
    @Transactional
    public EventDetailResponse update(
            UUID id,
            EventRequest request,
            MultipartFile file
    ) {

        validateDates(request);

        Event event = findAndValidate(id);

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

        return eventMapper.detail(saved);
    }

    private String buildPath(UUID orgId, UUID eventId) {
        return orgId + "/" + eventId + "/template.png";
    }

    @Override
    @Transactional(readOnly = true)
    public EventDetailResponse getById(
            UUID id
    ) {

        Event event =
                findAndValidate(id);

        return eventMapper.detail(event);
    }

    private Event findAndValidate(
            UUID id
    ) {

        Event event =
                eventRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Evento no encontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (!event.getOrganization()
                .getId()
                .equals(
                        authContext.getOrganizationId()
                )) {

            throw new Exceptions(
                    "No tiene permisos sobre este evento",
                    HttpStatus.FORBIDDEN
            );
        }

        return event;
    }

    @Override
    @Transactional
    public EventDetailResponse publish(
            UUID id
    ) {

        Event event =
                findAndValidate(id);

        if (event.getStatus() == EventStatus.CANCELLED) {

            throw new Exceptions(
                    "No se puede publicar un evento cancelado",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (event.getStatus() == EventStatus.FINISHED) {

            throw new Exceptions(
                    "No se puede publicar un evento finalizado",
                    HttpStatus.BAD_REQUEST
            );
        }

        event.setStatus(
                EventStatus.PUBLISHED
        );

        eventRepository.save(event);

        return eventMapper.detail(event);
    }

    @Override
    @Transactional
    public EventDetailResponse cancel(
            UUID id
    ) {

        Event event =
                findAndValidate(id);

        if (event.getStatus() == EventStatus.FINISHED) {

            throw new Exceptions(
                    "No se puede cancelar un evento finalizado",
                    HttpStatus.BAD_REQUEST
            );
        }

        event.setStatus(
                EventStatus.CANCELLED
        );

        eventRepository.save(event);

        return eventMapper.detail(event);
    }

    @Override
    public Event findById(UUID id) {

        return eventRepository
                .findByIdAndOrganization_Id(id, authContext.getOrganizationId())
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
                    "La fecha fin debe ser mayor a la fecha inicio",
                    HttpStatus.BAD_REQUEST
            );
        }
    }
}
