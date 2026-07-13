package pe.dcs.app.features.membership_user;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/membership-user")
@RequiredArgsConstructor
public class MembershipUserController {

    /*private final MembershipUserSearchService service;

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
    }*/
}