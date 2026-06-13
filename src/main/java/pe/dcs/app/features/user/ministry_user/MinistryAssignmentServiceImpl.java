package pe.dcs.app.features.user.ministry_user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.MemberMinistryAssignment;
import pe.dcs.app.entity.Ministry;
import pe.dcs.app.entity.MinistryRole;
import pe.dcs.app.entity.User;
import pe.dcs.app.features.user.ministry_user.validators.MinistryAssignmentValidator;
import pe.dcs.app.features.user.ministry_user.mapper.MinistryAssignmentMapper;
import pe.dcs.app.features.user.ministry_user.request.MinistryAssignmentRequest;
import pe.dcs.app.features.user.ministry_user.response.MinistryAssignmentResponse;
import pe.dcs.app.features.user.ministry_user.response.MinistryGroupedResponse;
import pe.dcs.app.features.user.ministry_user.response.MinistryOptionResponse;
import pe.dcs.app.features.user.ministry_user.response.MinistryRoleOptionResponse;
import pe.dcs.app.features.user.ministry_user.service.MinistryAssignmentService;
import pe.dcs.app.repository.MemberMinistryAssignmentRepository;
import pe.dcs.app.repository.MinistryRepository;
import pe.dcs.app.repository.MinistryRoleRepository;
import pe.dcs.app.security.service.AuthContext;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class MinistryAssignmentServiceImpl
        implements MinistryAssignmentService {

    private final MemberMinistryAssignmentRepository assignmentRepository;
    private final MinistryRepository ministryRepository;
    private final MinistryRoleRepository ministryRoleRepository;
    private final MinistryAssignmentValidator validator;
    private final AuthContext authContext;

    // =========================================================
    // CREATE
    // =========================================================

    @Override
    public MinistryAssignmentResponse create(
            UUID userId,
            MinistryAssignmentRequest request
    ) {

        UUID authUserId = authContext.getUserId();

        User targetUser = validator.getUser(userId);
        User authUser = validator.getUser(authUserId);

        // MULTI-TENANT SAFETY
        validator.validateSameOrganization(authUser, targetUser);

        // only active members can serve
        validator.validateActiveMembership(userId);

        validator.validateDates(request.getStartDate(), request.getEndDate());

        Ministry ministry = validator.getMinistry(request.getMinistryId());
        MinistryRole role = validator.getRole(request.getMinistryRoleId());

        validator.validateRoleBelongsToMinistry(role, ministry);

        // only one open service
        if (request.getEndDate() == null) {
            validator.validateSingleOpenAssignment(
                    userId,
                    ministry.getId(),
                    null
            );
        }

        validator.validateOverlap(
                userId,
                ministry.getId(),
                request.getStartDate(),
                request.getEndDate()
        );

        MemberMinistryAssignment assignment = new MemberMinistryAssignment();

        assignment.setUser(targetUser);
        assignment.setMinistry(ministry);
        assignment.setMinistryRole(role);
        assignment.setStartDate(request.getStartDate());
        assignment.setEndDate(request.getEndDate());
        assignment.setReason(request.getReason());
        assignment.setObservation(request.getObservation());

        return MinistryAssignmentMapper.toAssignmentResponse(
                assignmentRepository.save(assignment)
        );
    }

    // =========================================================
    // UPDATE
    // =========================================================

    @Override
    public MinistryAssignmentResponse update(
            UUID userId,
            UUID assignmentId,
            MinistryAssignmentRequest request
    ) {

        UUID authUserId = authContext.getUserId();

        MemberMinistryAssignment assignment =
                validator.getAssignment(assignmentId);

        // ownership (ya incluye lógica de seguridad interna)
        validator.validateOwnership(userId, assignment);

        // extra safety: org boundary
        validator.validateSameOrganization(
                assignment.getUser(),
                validator.getUser(authUserId)
        );

        validator.validateDates(request.getStartDate(), request.getEndDate());

        UUID ministryId = assignment.getMinistry().getId();

        if (request.getEndDate() == null) {
            validator.validateSingleOpenAssignment(
                    userId,
                    ministryId,
                    assignmentId
            );
        }

        validator.validateOverlapExcludingSelf(
                userId,
                ministryId,
                assignmentId,
                request.getStartDate(),
                request.getEndDate()
        );

        assignment.setStartDate(request.getStartDate());
        assignment.setEndDate(request.getEndDate());
        assignment.setReason(request.getReason());
        assignment.setObservation(request.getObservation());

        return MinistryAssignmentMapper.toAssignmentResponse(
                assignmentRepository.save(assignment)
        );
    }

    // =========================================================
    // DELETE
    // =========================================================

    @Override
    public void delete(
            UUID userId,
            UUID assignmentId
    ) {

        UUID authUserId = authContext.getUserId();

        MemberMinistryAssignment assignment =
                validator.getAssignment(assignmentId);

        validator.validateOwnership(userId, assignment);

        validator.validateSameOrganization(
                assignment.getUser(),
                validator.getUser(authUserId)
        );

        assignmentRepository.delete(assignment);
    }

    // =========================================================
    // GET SERVICES
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<MinistryGroupedResponse> getServices(UUID userId) {

        validator.getUser(userId);

        return MinistryAssignmentMapper.toGroupedResponse(
                assignmentRepository.findAllByUserId(userId)
        );
    }

    // =========================================================
    // GET MINISTRIES
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<MinistryOptionResponse> getMinistries() {

        return ministryRepository
                .findByActiveTrueOrderByNameAsc()
                .stream()
                .map(MinistryAssignmentMapper::toMinistryOptionResponse)
                .toList();
    }

    // =========================================================
    // GET ROLES
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<MinistryRoleOptionResponse> getRoles() {

        return ministryRoleRepository
                .findByActiveTrueOrderByNameAsc()
                .stream()
                .map(MinistryAssignmentMapper::toRoleOptionResponse)
                .toList();
    }
}