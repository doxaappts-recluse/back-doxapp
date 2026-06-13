package pe.dcs.app.features.user.ministry_user.validators;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.entity.MemberMinistryAssignment;
import pe.dcs.app.entity.Ministry;
import pe.dcs.app.entity.MinistryRole;
import pe.dcs.app.entity.User;
import pe.dcs.app.repository.*;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.membership.MembershipStatus;

import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MinistryAssignmentValidator {

    private static final LocalDate MAX_DATE =
            LocalDate.of(9999, 12, 31);

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final MinistryRepository ministryRepository;
    private final MinistryRoleRepository ministryRoleRepository;
    private final MemberMinistryAssignmentRepository assignmentRepository;
    private final AuthContext authContext;

    // =========================================================
    // GET USER
    // =========================================================

    public User getUser(UUID userId) {

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "User not found",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        assertSameOrganization(user);

        return user;
    }

    // =========================================================
    // GET ASSIGNMENT
    // =========================================================

    public MemberMinistryAssignment getAssignment(
            UUID assignmentId
    ) {

        return assignmentRepository
                .findById(assignmentId)
                .orElseThrow(() ->
                        new Exceptions(
                                "Ministry assignment not found",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    // =========================================================
    // GET MINISTRY
    // =========================================================

    public Ministry getMinistry(
            UUID ministryId
    ) {

        return ministryRepository
                .findById(ministryId)
                .orElseThrow(() ->
                        new Exceptions(
                                "Ministry not found",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    // =========================================================
    // GET ROLE
    // =========================================================

    public MinistryRole getRole(
            UUID roleId
    ) {

        return ministryRoleRepository
                .findById(roleId)
                .orElseThrow(() ->
                        new Exceptions(
                                "Ministry role not found",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    // =========================================================
    // ORGANIZATION
    // =========================================================

    public void assertSameOrganization(
            User user
    ) {

        UUID organizationId =
                authContext.getOrganizationId();

        if (!user.getOrganization()
                .getId()
                .equals(organizationId)) {

            throw new Exceptions(
                    "Different organization",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    // =========================================================
    // ACTIVE MEMBERSHIP
    // CREATE ONLY
    // =========================================================

    public void validateActiveMembership(
            UUID userId
    ) {

        boolean exists =
                membershipRepository
                        .existsByUserIdAndCurrentTrueAndStatus(
                                userId,
                                MembershipStatus.ACTIVE
                        );

        if (!exists) {
            throw new Exceptions(
                    "User must have an active membership",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    // =========================================================
    // DATES
    // =========================================================

    public void validateDates(
            LocalDate startDate,
            LocalDate endDate
    ) {

        if (startDate == null) {
            throw new Exceptions(
                    "Start date is required",
                    HttpStatus.BAD_REQUEST
            );
        }

        LocalDate today =
                LocalDate.now();

        if (startDate.isAfter(today)) {
            throw new Exceptions(
                    "Start date cannot be greater than today",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (endDate != null) {

            if (endDate.isAfter(today)) {
                throw new Exceptions(
                        "End date cannot be greater than today",
                        HttpStatus.BAD_REQUEST
                );
            }

            if (endDate.isBefore(startDate)) {
                throw new Exceptions(
                        "End date cannot be less than start date",
                        HttpStatus.BAD_REQUEST
                );
            }
        }
    }

    // =========================================================
    // ROLE ↔ MINISTRY
    // =========================================================

    public void validateRoleBelongsToMinistry(
            MinistryRole role,
            Ministry ministry
    ) {

        // global role
        if (role.getMinistry() == null) {
            return;
        }

        boolean valid =
                role.getMinistry()
                        .getId()
                        .equals(ministry.getId());

        if (!valid) {
            throw new Exceptions(
                    "Role does not belong to ministry",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    // =========================================================
    // ONLY ONE OPEN ASSIGNMENT
    // =========================================================

    public void validateSingleOpenAssignment(
            UUID userId,
            UUID ministryId,
            UUID assignmentId
    ) {

        boolean exists;

        if (assignmentId == null) {

            exists =
                    assignmentRepository
                            .existsByUserIdAndMinistryIdAndEndDateIsNull(
                                    userId,
                                    ministryId
                            );

        } else {

            exists =
                    assignmentRepository
                            .existsByUserIdAndMinistryIdAndEndDateIsNullAndIdNot(
                                    userId,
                                    ministryId,
                                    assignmentId
                            );
        }

        if (exists) {
            throw new Exceptions(
                    "Only one active assignment is allowed per ministry",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    // =========================================================
    // OVERLAP CREATE
    // =========================================================

    public void validateOverlap(
            UUID userId,
            UUID ministryId,
            LocalDate startDate,
            LocalDate endDate
    ) {

        boolean overlap;

        if (endDate == null) {

            overlap = assignmentRepository.existsOverlapOpen(
                    userId,
                    ministryId,
                    startDate,
                    MAX_DATE
            );

        } else {

            overlap = assignmentRepository.existsOverlapClosed(
                    userId,
                    ministryId,
                    startDate,
                    endDate,
                    MAX_DATE
            );
        }

        if (overlap) {
            throw new Exceptions(
                    "Assignment dates overlap with another ministry service",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    // =========================================================
    // OVERLAP UPDATE
    // =========================================================

    public void validateOverlapExcludingSelf(
            UUID userId,
            UUID ministryId,
            UUID assignmentId,
            LocalDate startDate,
            LocalDate endDate
    ) {

        boolean overlap;

        if (endDate == null) {

            overlap =
                    assignmentRepository
                            .existsOverlapOpenExcludingSelf(
                                    userId,
                                    ministryId,
                                    assignmentId,
                                    startDate,
                                    MAX_DATE
                            );

        } else {

            overlap =
                    assignmentRepository
                            .existsOverlapClosedExcludingSelf(
                                    userId,
                                    ministryId,
                                    assignmentId,
                                    startDate,
                                    endDate,
                                    MAX_DATE
                            );
        }

        if (overlap) {
            throw new Exceptions(
                    "Assignment dates overlap with another ministry service",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    // =========================================================
    // OWNERSHIP
    // =========================================================

    public void validateOwnership(
            UUID userId,
            MemberMinistryAssignment assignment
    ) {

        if (!assignment.getUser()
                .getId()
                .equals(userId)) {

            throw new Exceptions(
                    "Assignment does not belong to user",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    public void validateSameOrganization(User authUser, User targetUser) {

        UUID authOrgId = authUser.getOrganization().getId();
        UUID targetOrgId = targetUser.getOrganization().getId();

        if (!authOrgId.equals(targetOrgId)) {
            throw new RuntimeException("Cross-organization access denied");
        }
    }
}