package pe.dcs.app.features.user.membership_user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.user.membership_user.request.MembershipHistoryRequest;
import pe.dcs.app.features.user.membership_user.request.MembershipRequest;
import pe.dcs.app.features.user.membership_user.request.MembershipUserSearchRequest;
import pe.dcs.app.features.user.membership_user.response.MembershipContextResponse;
import pe.dcs.app.features.user.membership_user.response.MembershipResponse;
import pe.dcs.app.features.user.membership_user.response.MembershipUserSearchResponse;
import pe.dcs.app.features.user.membership_user.service.MembershipUserSearchService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/membership-user")
@RequiredArgsConstructor
public class MembershipUserController {

    private final MembershipUserSearchService service;

    @PostMapping("/search")
    public ApiResponse<PageResponse<MembershipUserSearchResponse>> search(
            @RequestBody MembershipUserSearchRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Membership users retrieved successfully",
                service.search(request)
        );
    }

    // =========================================================
    // CREATE
    // =========================================================

    @PostMapping("/create/{userId}")
    public ApiResponse<MembershipResponse> create(
            @PathVariable UUID userId,
            @RequestBody MembershipRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.CREATED.value(),
                "Membership created successfully",
                service.create(userId, request)
        );
    }

    // =========================================================
    // UPDATE
    // =========================================================

    @PutMapping("/update/{userId}/{membershipId}")
    public ApiResponse<MembershipResponse> update(
            @PathVariable UUID userId,
            @PathVariable UUID membershipId,
            @RequestBody MembershipRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Membership updated successfully",
                service.update(userId, membershipId, request)
        );
    }

    // =========================================================
    // CURRENT MEMBERSHIP
    // =========================================================

    @GetMapping("/current/{userId}")
    public ApiResponse<MembershipContextResponse> getCurrentMembership(
            @PathVariable UUID userId
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Current membership retrieved successfully",
                service.getCurrentMembership(userId)
        );
    }

    // =========================================================
    // HISTORY
    // =========================================================

    @PostMapping("/history/{userId}")
    public ApiResponse<PageResponse<MembershipResponse>> getHistory(
            @PathVariable UUID userId,
            @RequestBody MembershipHistoryRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Membership history retrieved successfully",
                service.getHistory(userId, request)
        );
    }
}