package pe.dcs.app.features.user.membership_user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Membership;
import pe.dcs.app.entity.User;
import pe.dcs.app.features.user.membership_user.mapper.MembershipMapper;
import pe.dcs.app.features.user.membership_user.request.MembershipHistoryRequest;
import pe.dcs.app.features.user.membership_user.request.MembershipRequest;
import pe.dcs.app.features.user.membership_user.response.MembershipContextResponse;
import pe.dcs.app.features.user.membership_user.response.MembershipResponse;
import pe.dcs.app.features.user.membership_user.response.MembershipUserResponse;
import pe.dcs.app.repository.MembershipRepository;
import pe.dcs.app.repository.UserRepository;
import pe.dcs.app.features.user.membership_user.mapper.MembershipUserMapper;
import pe.dcs.app.features.user.membership_user.request.MembershipUserFilterRequest;
import pe.dcs.app.features.user.membership_user.request.MembershipUserSearchRequest;
import pe.dcs.app.features.user.membership_user.response.MembershipUserSearchResponse;
import pe.dcs.app.features.user.membership_user.service.MembershipUserSearchService;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.membership.MembershipStatus;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MembershipUserSearchServiceImpl implements MembershipUserSearchService {

    private final UserRepository userRepository;
    private final AuthContext authContext;
    private final MembershipRepository membershipRepository;

    @Override
    public PageResponse<MembershipUserSearchResponse> search(
            MembershipUserSearchRequest request
    ) {

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        UUID organizationId =
                authContext.getOrganizationId();

        MembershipUserFilterRequest filters =
                request.getFilters();

        Page<User> page =
                userRepository.findAll(
                        MembershipUserSpecification.filter(
                                organizationId,
                                filters != null
                                        ? filters.getName()
                                        : null,
                                filters != null
                                        ? filters.getLastname()
                                        : null,
                                filters != null
                                        ? filters.getHasMembership()
                                        : null,
                                filters != null
                                        ? filters.getMembershipStatus()
                                        : null
                        ),
                        pageable
                );

        List<MembershipUserSearchResponse> content =
                page.getContent()
                        .stream()
                        .map(MembershipUserMapper::map)
                        .toList();

        return new PageResponse<>(
                content,
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    // =========================================================
    // CREATE
    // =========================================================

    @Override
    public MembershipResponse create(UUID userId, MembershipRequest request) {

        User user = getUser(userId);

        assertSameOrganization(user);

        Membership current =
                membershipRepository
                        .findByUserIdAndCurrentTrue(userId)
                        .orElse(null);

        // =========================================
        // CLOSE CURRENT MEMBERSHIP
        // =========================================
        if (current != null) {

            if (!request.getStartDate()
                    .isAfter(current.getStartDate())) {

                throw new Exceptions(
                        "Membership start date must be greater than current membership start date",
                        HttpStatus.BAD_REQUEST
                );
            }

            current.setCurrent(false);
            current.setStatus(
                    MembershipStatus.INACTIVE
            );

            current.setEndDate(
                    request.getStartDate()
                            .minusDays(1)
            );

            membershipRepository.save(current);
        }

        // =========================================
        // CREATE MEMBERSHIP
        // =========================================
        Membership membership =
                new Membership();

        membership.setUser(user);

        membership.setStartDate(
                request.getStartDate()
        );

        membership.setStatus(
                request.getStatus()
        );

        membership.setExitReason(
                request.getExitReason()
        );

        membership.setReason(
                request.getReason()
        );

        membership.setNotes(
                request.getNotes()
        );

        membership.setCurrent(true);

        membership =
                membershipRepository
                        .save(membership);

        return MembershipMapper
                .toResponse(membership);
    }

    // =========================================================
    // UPDATE CURRENT MEMBERSHIP
    // =========================================================

    @Override
    public MembershipResponse update(
            UUID userId,
            UUID membershipId,
            MembershipRequest request
    ) {

        User user = getUser(userId);

        assertSameOrganization(user);

        Membership membership =
                membershipRepository
                        .findById(membershipId)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Membership not found",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        // membership belongs to user
        if (!membership.getUser()
                .getId()
                .equals(userId)) {

            throw new Exceptions(
                    "Membership does not belong to user",
                    HttpStatus.BAD_REQUEST
            );
        }

        // only current editable
        if (Boolean.FALSE.equals(
                membership.getCurrent()
        )) {

            throw new Exceptions(
                    "Historical memberships cannot be edited",
                    HttpStatus.BAD_REQUEST
            );
        }

        // startDate intentionally not editable

        membership.setStatus(
                request.getStatus()
        );

        membership.setExitReason(
                request.getExitReason()
        );

        membership.setReason(
                request.getReason()
        );

        membership.setNotes(
                request.getNotes()
        );

        membership =
                membershipRepository
                        .save(membership);

        return MembershipMapper
                .toResponse(membership);
    }

    // =========================================================
    // GET CURRENT MEMBERSHIP
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public MembershipContextResponse getCurrentMembership(UUID userId) {

        User user = getUser(userId);

        assertSameOrganization(user);

        Membership membership =
                membershipRepository
                        .findByUserIdAndCurrentTrue(userId)
                        .orElse(null);

        return new MembershipContextResponse(
                new MembershipUserResponse(
                        user.getId(),
                        user.getName(),
                        user.getLastname()
                ),
                membership != null
                        ? MembershipMapper.toResponse(membership)
                        : null
        );
    }

    // =========================================================
    // HISTORY
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MembershipResponse> getHistory(
            UUID userId,
            MembershipHistoryRequest request
    ) {

        User user = getUser(userId);
        assertSameOrganization(user);

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        Page<Membership> page =
                membershipRepository.findByUserId(userId, pageable);

        List<MembershipResponse> content =
                page.getContent()
                        .stream()
                        .map(MembershipMapper::toResponse)
                        .toList();

        return new PageResponse<>(
                content,
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private User getUser(UUID userId) {

        return userRepository
                .findById(userId)
                .orElseThrow(() ->
                        new Exceptions(
                                "User not found",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    private void assertSameOrganization(
            User user
    ) {

        UUID organizationId =
                authContext
                        .getOrganizationId();

        if (user.getOrganization() == null
                || !user.getOrganization()
                .getId()
                .equals(organizationId)) {

            throw new Exceptions(
                    "User does not belong to your organization",
                    HttpStatus.FORBIDDEN
            );
        }
    }
}