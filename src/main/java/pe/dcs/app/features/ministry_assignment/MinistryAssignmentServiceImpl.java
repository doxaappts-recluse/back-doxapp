package pe.dcs.app.features.ministry_assignment;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Ministry;
import pe.dcs.app.entity.MinistryAssignment;
import pe.dcs.app.entity.MinistryRole;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.features.ministry_assignment.mapper.MinistryAssignmentMapper;
import pe.dcs.app.features.ministry_assignment.request.MinistryAssignmentCreateRequest;
import pe.dcs.app.features.ministry_assignment.request.MinistryAssignmentUpdateRequest;
import pe.dcs.app.features.ministry_assignment.response.MinistryAssignmentGroupedResponse;
import pe.dcs.app.features.ministry_assignment.response.MinistryAssignmentResponse;
import pe.dcs.app.features.ministry_assignment.response.MinistryRoleSimpleResponse;
import pe.dcs.app.features.ministry_assignment.response.MinistrySimpleResponse;
import pe.dcs.app.features.ministry_assignment.service.MinistryAssignmentService;
import pe.dcs.app.repository.MinistryAssignmentRepository;
import pe.dcs.app.repository.MinistryRepository;
import pe.dcs.app.repository.MinistryRoleRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MinistryAssignmentServiceImpl implements MinistryAssignmentService {

    private final PersonRepository personRepository;
    private final MinistryRepository ministryRepository;
    private final MinistryRoleRepository ministryRoleRepository;
    private final MinistryAssignmentRepository ministryAssignmentRepository;
    private final MinistryAssignmentMapper mapper;
    private final AuthContext authContext;

    // =====================================================
    // CREATE
    // =====================================================

    @Override
    @Transactional
    public MinistryAssignmentResponse create(UUID userId, MinistryAssignmentCreateRequest request) {

        Person person = findPersonOrThrow(userId);

        validateAccess(person);

        Ministry ministry = findMinistryOrThrow(request.getMinistryId());
        MinistryRole role = findRoleOrThrow(request.getMinistryRoleId());

        validateRoleBelongsToMinistry(role, ministry);
        validateDates(request.getStartDate(), request.getEndDate());
        validateNoOverlap(userId, ministry.getId(), null, request.getStartDate(), request.getEndDate());

        MinistryAssignment assignment = new MinistryAssignment();

        assignment.setPerson(person);
        assignment.setMinistry(ministry);
        assignment.setMinistryRole(role);
        assignment.setStartDate(request.getStartDate());
        assignment.setEndDate(request.getEndDate());
        assignment.setReason(request.getReason());
        assignment.setObservation(request.getObservation());

        ministryAssignmentRepository.save(assignment);

        return mapper.toResponse(assignment);
    }

    // =====================================================
    // UPDATE
    // =====================================================

    @Override
    @Transactional
    public MinistryAssignmentResponse update(UUID userId, UUID assignmentId, MinistryAssignmentUpdateRequest request) {

        MinistryAssignment assignment = findAssignmentOrThrow(assignmentId);

        validateBelongsToPerson(assignment, userId);
        validateAccess(assignment.getPerson());

        validateDates(request.getStartDate(), request.getEndDate());

        validateNoOverlap(
                userId,
                assignment.getMinistry().getId(),
                assignmentId,
                request.getStartDate(),
                request.getEndDate()
        );

        assignment.setStartDate(request.getStartDate());
        assignment.setEndDate(request.getEndDate());
        assignment.setReason(request.getReason());
        assignment.setObservation(request.getObservation());

        ministryAssignmentRepository.save(assignment);

        return mapper.toResponse(assignment);
    }

    // =====================================================
    // DELETE
    // =====================================================

    @Override
    @Transactional
    public void delete(UUID userId, UUID assignmentId) {

        MinistryAssignment assignment = findAssignmentOrThrow(assignmentId);

        validateBelongsToPerson(assignment, userId);
        validateAccess(assignment.getPerson());

        ministryAssignmentRepository.delete(assignment);
    }

    // =====================================================
    // GET BY USER (agrupado por ministerio)
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public List<MinistryAssignmentGroupedResponse> getByUser(UUID userId) {

        Person person = findPersonOrThrow(userId);

        validateAccess(person);

        List<MinistryAssignment> history =
                ministryAssignmentRepository.findByPersonIdOrderByStartDateDesc(userId);

        Map<UUID, MinistryAssignmentGroupedResponse> grouped = new LinkedHashMap<>();

        for (MinistryAssignment assignment : history) {

            UUID ministryId = assignment.getMinistry().getId();

            MinistryAssignmentGroupedResponse group =
                    grouped.computeIfAbsent(ministryId, id -> {

                        MinistryAssignmentGroupedResponse response = new MinistryAssignmentGroupedResponse();

                        response.setMinistryId(id);
                        response.setMinistryName(assignment.getMinistry().getName());
                        response.setAssignments(new ArrayList<>());

                        return response;
                    });

            group.getAssignments().add(mapper.toGroupedItem(assignment));
        }

        return new ArrayList<>(grouped.values());
    }

    // =====================================================
    // CATALOGOS
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public List<MinistrySimpleResponse> getAllMinistries() {

        return ministryRepository.findAllByStatusOrderByNameAsc(StatusType.ACTIVE)
                .stream()
                .map(mapper::toMinistrySimple)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MinistryRoleSimpleResponse> getAllRoles() {

        return ministryRoleRepository.findAllByStatusOrderByNameAsc(StatusType.ACTIVE)
                .stream()
                .map(mapper::toRoleSimple)
                .toList();
    }

    // =====================================================
    // HELPERS
    // =====================================================

    /**
     * Es un registro histórico: los periodos de una misma persona
     * DENTRO DEL MISMO MINISTERIO no pueden solaparse en fechas.
     * No hay restricción entre ministerios distintos.
     */
    private void validateNoOverlap(
            UUID personId,
            UUID ministryId,
            UUID excludeAssignmentId,
            LocalDate startDate,
            LocalDate endDate
    ) {

        List<MinistryAssignment> history =
                ministryAssignmentRepository.findByPersonIdOrderByStartDateDesc(personId);

        for (MinistryAssignment existing : history) {

            if (!existing.getMinistry().getId().equals(ministryId)) {
                continue;
            }

            if (excludeAssignmentId != null
                    && excludeAssignmentId.equals(existing.getId())) {
                continue;
            }

            LocalDate existingStart = existing.getStartDate();
            LocalDate existingEnd = existing.getEndDate();

            boolean overlaps =
                    (endDate == null || !endDate.isBefore(existingStart))
                            && (existingEnd == null || !startDate.isAfter(existingEnd));

            if (overlaps) {

                throw new Exceptions(
                        "La persona ya sirve en este ministerio durante ese periodo. Solo puede haber un periodo por vez en el mismo ministerio.",
                        HttpStatus.CONFLICT
                );
            }
        }
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {

        if (startDate == null) {
            throw new Exceptions(
                    "La fecha de inicio es obligatoria.",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (endDate != null && endDate.isBefore(startDate)) {
            throw new Exceptions(
                    "La fecha de fin no puede ser anterior a la fecha de inicio.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validateRoleBelongsToMinistry(MinistryRole role, Ministry ministry) {

        if (role.getMinistry() == null
                || !role.getMinistry().getId().equals(ministry.getId())) {

            throw new Exceptions(
                    "El rol seleccionado no pertenece al ministerio seleccionado.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validateBelongsToPerson(MinistryAssignment assignment, UUID userId) {

        if (assignment.getPerson() == null
                || !assignment.getPerson().getId().equals(userId)) {

            throw new Exceptions(
                    "El registro de servicio ministerial no pertenece a esta persona.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Caso especial: la asignación a ministerios solo la puede
     * gestionar un administrador de organización o de sede
     * (nunca SYSTEM, que queda fuera de este flujo a propósito).
     */
    private void validateAccess(Person person) {

        PersonBranch activeBranch =
                person.getBranchHistory()
                        .stream()
                        .filter(pb -> pb.getStatus() == StatusType.ACTIVE)
                        .findFirst()
                        .orElse(null);

        if (activeBranch == null) {
            throw new Exceptions(
                    "La persona no tiene una sede activa.",
                    HttpStatus.CONFLICT
            );
        }

        UUID organizationId =
                activeBranch.getBranch().getOrganization().getId();

        UUID branchId =
                activeBranch.getBranch().getId();

        if (!authContext.canManageOrgOrBranchOnly(organizationId, branchId)) {

            throw new Exceptions(
                    "Solo un administrador de organización o de sede puede gestionar el servicio ministerial de esta persona.",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private Person findPersonOrThrow(UUID id) {

        return personRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions(
                                "Persona no encontrada.",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    private Ministry findMinistryOrThrow(UUID id) {

        return ministryRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions(
                                "Ministerio no encontrado.",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    private MinistryRole findRoleOrThrow(UUID id) {

        return ministryRoleRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions(
                                "Rol de ministerio no encontrado.",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    private MinistryAssignment findAssignmentOrThrow(UUID id) {

        return ministryAssignmentRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions(
                                "Registro de servicio ministerial no encontrado.",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

}
