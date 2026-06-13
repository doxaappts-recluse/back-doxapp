package pe.dcs.app.features.user.ministry_user.mapper;

import pe.dcs.app.entity.MemberMinistryAssignment;
import pe.dcs.app.entity.Ministry;
import pe.dcs.app.entity.MinistryRole;
import pe.dcs.app.features.ministry_role.response.MinistryRoleResponse;
import pe.dcs.app.features.user.ministry_user.response.MinistryAssignmentResponse;
import pe.dcs.app.features.user.ministry_user.response.MinistryGroupedResponse;
import pe.dcs.app.features.user.ministry_user.response.MinistryOptionResponse;
import pe.dcs.app.features.user.ministry_user.response.MinistryRoleOptionResponse;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class MinistryAssignmentMapper {

    private MinistryAssignmentMapper() {}

    // =====================================================
    // ASSIGNMENT RESPONSE
    // =====================================================

    public static MinistryAssignmentResponse toAssignmentResponse(MemberMinistryAssignment assignment) {

        MinistryRole role =
                assignment.getMinistryRole();

        return new MinistryAssignmentResponse(
                assignment.getId(),

                role != null
                        ? role.getId()
                        : null,

                role != null
                        ? role.getName()
                        : null,

                assignment.getStartDate(),

                assignment.getEndDate(),

                assignment.getReason(),

                assignment.getObservation(),

                assignment.getEndDate() == null
        );
    }

    // =====================================================
    // MINISTRY OPTION
    // =====================================================

    public static MinistryOptionResponse
    toMinistryOptionResponse(
            Ministry ministry
    ) {

        return new MinistryOptionResponse (
                ministry.getId(),
                ministry.getName()
        );
    }

    // =====================================================
    // ROLE OPTION
    // =====================================================

    public static MinistryRoleOptionResponse
    toRoleOptionResponse(
            MinistryRole role
    ) {

        Ministry ministry =
                role.getMinistry();

        return new MinistryRoleOptionResponse(
                role.getId(),

                role.getName(),

                ministry != null
                        ? ministry.getId()
                        : null,

                ministry != null
                        ? ministry.getName()
                        : null
        );
    }

    // =====================================================
    // GROUPED RESPONSE
    // =====================================================

    public static List<MinistryGroupedResponse>
    toGroupedResponse(
            List<MemberMinistryAssignment>
                    assignments
    ) {

        Map<UUID,
                List<MemberMinistryAssignment>>
                grouped =
                assignments.stream()
                        .collect(
                                Collectors.groupingBy(
                                        a -> a.getMinistry()
                                                .getId()
                                )
                        );

        return grouped.entrySet()
                .stream()
                .map(entry -> {

                    MemberMinistryAssignment first =
                            entry.getValue()
                                    .get(0);

                    return new MinistryGroupedResponse(
                            first.getMinistry()
                                    .getId(),

                            first.getMinistry()
                                    .getName(),

                            entry.getValue()
                                    .stream()
                                    .sorted(
                                            Comparator.comparing(
                                                            MemberMinistryAssignment::getStartDate
                                                    )
                                                    .reversed()
                                    )
                                    .map(
                                            MinistryAssignmentMapper
                                                    ::toAssignmentResponse
                                    )
                                    .toList()
                    );
                })
                .sorted(
                        Comparator.comparing(
                                MinistryGroupedResponse
                                        ::getMinistryName
                        )
                )
                .toList();
    }
}