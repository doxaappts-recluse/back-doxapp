package pe.dcs.app.features.space_reservation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.ReservableSpace;
import pe.dcs.app.entity.SpaceReservation;
import pe.dcs.app.features.space_reservation.mapper.SpaceReservationMapper;
import pe.dcs.app.features.space_reservation.request.ReservableSpaceFormRequest;
import pe.dcs.app.features.space_reservation.request.ReservableSpaceSearchRequest;
import pe.dcs.app.features.space_reservation.request.SpaceReservationFormRequest;
import pe.dcs.app.features.space_reservation.request.SpaceReservationSearchRequest;
import pe.dcs.app.features.space_reservation.response.ReservableSpaceResponse;
import pe.dcs.app.features.space_reservation.response.SpaceReservationPersonSearchResponse;
import pe.dcs.app.features.space_reservation.response.SpaceReservationResponse;
import pe.dcs.app.features.space_reservation.service.SpaceReservationService;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.MembershipRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.repository.ReservableSpaceRepository;
import pe.dcs.app.repository.SpaceReservationRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.space_reservation.ReservationSourceType;
import pe.dcs.app.util.enums.space_reservation.ReservationStatus;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reservas de Espacios. Catálogo de espacios (ReservableSpace) por
 * sede, no delegable. Reservas (SpaceReservation) delegables a la
 * sede, con solapamiento de horario bloqueado por espacio (ver
 * assertNoOverlap) y confirmación inmediata (sin flujo de
 * aprobación). Pueden vincularse a un Evento/Grupo Pequeño/Dictado
 * existente vía sourceType+sourceId SIN llave foránea real — este
 * módulo no consulta las tablas de esos otros módulos a propósito,
 * para no acoplarse con ellos (ver doc de SpaceReservation).
 */
@Service
@RequiredArgsConstructor
public class SpaceReservationServiceImpl implements SpaceReservationService {

    private final ReservableSpaceRepository reservableSpaceRepository;
    private final SpaceReservationRepository spaceReservationRepository;
    private final BranchRepository branchRepository;
    private final PersonRepository personRepository;
    private final MembershipRepository membershipRepository;
    private final SpaceReservationMapper mapper;
    private final AuthContext authContext;
    private final SpaceReservationAccessGuard accessGuard;

    // =====================================================
    // BUSCAR PERSONA POR DNI (responsable de una reserva)
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public SpaceReservationPersonSearchResponse findPersonByDni(String dni) {

        accessGuard.assertCanUse();

        UUID organizationId = authContext.getCurrentOrganizationId();

        if (organizationId == null) {
            throw new Exceptions("No tiene un contexto de organización activo.", HttpStatus.FORBIDDEN);
        }

        if (dni == null || dni.isBlank()) {
            throw new Exceptions("El DNI es obligatorio.", HttpStatus.BAD_REQUEST);
        }

        Person person =
                personRepository.findByDniInOrganization(dni, organizationId)
                        .orElseThrow(() -> new Exceptions(
                                "No se encontró ninguna persona con ese DNI en la organización.",
                                HttpStatus.NOT_FOUND
                        ));

        boolean isMember =
                membershipRepository.existsByPersonIdAndCurrentTrueAndStatus(person.getId(), StatusType.ACTIVE);

        SpaceReservationPersonSearchResponse response = new SpaceReservationPersonSearchResponse();
        response.setPersonId(person.getId());
        response.setName(person.getName());
        response.setLastname(person.getLastname());
        response.setDni(person.getDni());
        response.setMember(isMember);

        return response;
    }

    // =====================================================
    // ESPACIOS
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReservableSpaceResponse> searchSpaces(ReservableSpaceSearchRequest request) {

        accessGuard.assertCanUse();

        Pageable pageable = PageableUtil.buildPageable(request.getPagination(), request.getSorts());

        Page<ReservableSpace> page =
                reservableSpaceRepository.findAll(
                        ReservableSpaceSpecification.filter(request.getFilters(), authContext),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(space -> mapper.toSpaceResponse(
                                space,
                                spaceReservationRepository.countBySpaceId(space.getId()),
                                accessGuard.canManageSpace(space),
                                showAudit
                        ))
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
    @Transactional(readOnly = true)
    public ReservableSpaceResponse getSpaceById(UUID id) {

        accessGuard.assertCanUse();

        ReservableSpace space = findSpaceOrThrow(id);

        return mapper.toSpaceResponse(
                space,
                spaceReservationRepository.countBySpaceId(space.getId()),
                accessGuard.canManageSpace(space),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional
    public UUID createSpace(ReservableSpaceFormRequest request) {

        accessGuard.assertCanCreateSpace();

        if (request.getName() == null || request.getName().isBlank()) {
            throw new Exceptions("El nombre del espacio es obligatorio.", HttpStatus.BAD_REQUEST);
        }

        UUID branchId = accessGuard.resolveBranchId(request.getBranchId());

        if (branchId == null) {
            throw new Exceptions("Debe seleccionar la sede del espacio.", HttpStatus.BAD_REQUEST);
        }

        ReservableSpace space = new ReservableSpace();
        space.setName(request.getName());
        space.setDescription(request.getDescription());
        space.setCapacity(request.getCapacity());
        space.setBranch(findBranchOrThrow(branchId));
        space.setStatus(request.getStatus() != null ? request.getStatus() : StatusType.ACTIVE);

        reservableSpaceRepository.save(space);

        return space.getId();
    }

    @Override
    @Transactional
    public void updateSpace(UUID id, ReservableSpaceFormRequest request) {

        ReservableSpace space = findSpaceOrThrow(id);

        accessGuard.assertCanManageSpace(space);

        if (request.getName() == null || request.getName().isBlank()) {
            throw new Exceptions("El nombre del espacio es obligatorio.", HttpStatus.BAD_REQUEST);
        }

        space.setName(request.getName());
        space.setDescription(request.getDescription());
        space.setCapacity(request.getCapacity());

        if (request.getStatus() != null) {
            space.setStatus(request.getStatus());
        }

        // La sede del espacio no se reasigna tras crearlo — mismo
        // criterio que BibleClass.courseId (evita bypasear el scope
        // por sede de las reservas ya hechas sobre este espacio).

        reservableSpaceRepository.save(space);
    }

    // =====================================================
    // RESERVAS
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SpaceReservationResponse> searchReservations(SpaceReservationSearchRequest request) {

        accessGuard.assertCanUse();

        Pageable pageable = PageableUtil.buildPageable(request.getPagination(), request.getSorts());

        Page<SpaceReservation> page =
                spaceReservationRepository.findAll(
                        SpaceReservationSpecification.filter(request.getFilters(), authContext),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(reservation -> mapper.toReservationResponse(
                                reservation,
                                accessGuard.canManageReservation(reservation),
                                showAudit
                        ))
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
    @Transactional(readOnly = true)
    public SpaceReservationResponse getReservationById(UUID id) {

        accessGuard.assertCanUse();

        SpaceReservation reservation = findReservationOrThrow(id);

        return mapper.toReservationResponse(
                reservation,
                accessGuard.canManageReservation(reservation),
                authContext.canViewAudit()
        );
    }

    @Override
    @Transactional
    public UUID createReservation(SpaceReservationFormRequest request) {

        accessGuard.assertCanCreateReservation();

        validateReservationForm(request);

        ReservableSpace space = findSpaceOrThrow(request.getSpaceId());

        assertNoOverlap(space.getId(), request.getStartDateTime(), request.getEndDateTime(), null);

        SpaceReservation reservation = new SpaceReservation();
        reservation.setSpace(space);
        reservation.setStatus(ReservationStatus.CONFIRMED);

        applyReservationForm(reservation, request);

        spaceReservationRepository.save(reservation);

        return reservation.getId();
    }

    @Override
    @Transactional
    public void updateReservation(UUID id, SpaceReservationFormRequest request) {

        SpaceReservation reservation = findReservationOrThrow(id);

        accessGuard.assertCanManageReservation(reservation);

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new Exceptions("Esta reserva ya está cancelada.", HttpStatus.BAD_REQUEST);
        }

        validateReservationForm(request);

        // El espacio de la reserva no se reasigna tras crearla —
        // mismo criterio que BibleClass.courseId — evita bypasear el
        // scope por sede de quien la gestiona.
        assertNoOverlap(reservation.getSpace().getId(), request.getStartDateTime(), request.getEndDateTime(), reservation.getId());

        applyReservationForm(reservation, request);

        spaceReservationRepository.save(reservation);
    }

    @Override
    @Transactional
    public void cancelReservation(UUID id) {

        SpaceReservation reservation = findReservationOrThrow(id);

        accessGuard.assertCanManageReservation(reservation);

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return;
        }

        reservation.setStatus(ReservationStatus.CANCELLED);

        spaceReservationRepository.save(reservation);
    }

    // =====================================================
    // HELPERS — VALIDACIÓN Y APLICACIÓN DEL FORM DE RESERVA
    // =====================================================

    private void validateReservationForm(SpaceReservationFormRequest request) {

        if (request.getSpaceId() == null) {
            throw new Exceptions("Debe seleccionar el espacio a reservar.", HttpStatus.BAD_REQUEST);
        }

        if (request.getStartDateTime() == null || request.getEndDateTime() == null) {
            throw new Exceptions("La fecha/hora de inicio y fin son obligatorias.", HttpStatus.BAD_REQUEST);
        }

        if (!request.getEndDateTime().isAfter(request.getStartDateTime())) {
            throw new Exceptions("La fecha/hora de fin debe ser posterior al inicio.", HttpStatus.BAD_REQUEST);
        }

        if (request.getPurpose() == null || request.getPurpose().isBlank()) {
            throw new Exceptions("El motivo/actividad de la reserva es obligatorio.", HttpStatus.BAD_REQUEST);
        }

        ReservationSourceType sourceType =
                request.getSourceType() != null ? request.getSourceType() : ReservationSourceType.OTHER;

        if (sourceType != ReservationSourceType.OTHER && request.getSourceId() == null) {
            throw new Exceptions(
                    "Debe seleccionar el registro vinculado (Evento/Grupo Pequeño/Dictado).",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void assertNoOverlap(UUID spaceId, LocalDateTime start, LocalDateTime end, UUID excludeReservationId) {

        boolean overlap =
                spaceReservationRepository.existsOverlap(
                        spaceId,
                        start,
                        end,
                        ReservationStatus.CONFIRMED,
                        excludeReservationId
                );

        if (overlap) {
            throw new Exceptions(
                    "Este espacio ya tiene una reserva confirmada que cruza con ese horario.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void applyReservationForm(SpaceReservation reservation, SpaceReservationFormRequest request) {

        reservation.setSourceType(
                request.getSourceType() != null ? request.getSourceType() : ReservationSourceType.OTHER
        );
        reservation.setSourceId(
                reservation.getSourceType() == ReservationSourceType.OTHER ? null : request.getSourceId()
        );
        reservation.setPurpose(request.getPurpose());
        reservation.setRequesterName(request.getRequesterName());
        reservation.setRequesterPerson(
                request.getRequesterPersonId() != null
                        ? findPersonOrThrow(request.getRequesterPersonId())
                        : null
        );
        reservation.setStartDateTime(request.getStartDateTime());
        reservation.setEndDateTime(request.getEndDateTime());
        reservation.setNotes(request.getNotes());
    }

    // =====================================================
    // HELPERS — GENÉRICOS
    // =====================================================

    private ReservableSpace findSpaceOrThrow(UUID id) {
        return reservableSpaceRepository.findById(id)
                .orElseThrow(() -> new Exceptions("Espacio no encontrado.", HttpStatus.NOT_FOUND));
    }

    private SpaceReservation findReservationOrThrow(UUID id) {
        return spaceReservationRepository.findById(id)
                .orElseThrow(() -> new Exceptions("Reserva no encontrada.", HttpStatus.NOT_FOUND));
    }

    private Branch findBranchOrThrow(UUID id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new Exceptions("Sede no encontrada.", HttpStatus.NOT_FOUND));
    }

    private Person findPersonOrThrow(UUID id) {
        return personRepository.findById(id)
                .orElseThrow(() -> new Exceptions("Persona no encontrada.", HttpStatus.NOT_FOUND));
    }
}
