package pe.dcs.app.features.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.event.request.finance.EventFinanceApproveRequest;
import pe.dcs.app.features.event.request.finance.EventFinanceRejectRequest;
import pe.dcs.app.features.event.request.finance.EventFinanceRequest;
import pe.dcs.app.features.event.request.finance.EventFinanceSearchRequest;
import pe.dcs.app.features.event.response.finance.EventFinanceResponse;
import pe.dcs.app.features.event.service.EventFinanceService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/event-finances")
@RequiredArgsConstructor
public class EventFinanceController {

    private final EventFinanceService eventFinanceService;

    @PostMapping("/create")
    public ApiResponse<EventFinanceResponse> create(
            @RequestBody EventFinanceRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Movimiento financiero creado correctamente",
                eventFinanceService.create(request)
        );
    }

    @PutMapping("/update/{id}")
    public ApiResponse<EventFinanceResponse> update(
            @PathVariable UUID id,
            @RequestBody EventFinanceRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Movimiento financiero actualizado correctamente",
                eventFinanceService.update(id, request)
        );
    }

    @PatchMapping("/approve/{id}")
    public ApiResponse<EventFinanceResponse> approve(
            @PathVariable UUID id,
            @RequestBody(required = false)
                    EventFinanceApproveRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Movimiento financiero aprobado correctamente",
                eventFinanceService.approve(id, request)
        );
    }

    @PatchMapping("/reject/{id}")
    public ApiResponse<EventFinanceResponse> reject(
            @PathVariable UUID id,
            @RequestBody EventFinanceRejectRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Movimiento financiero rechazado correctamente",
                eventFinanceService.reject(id, request)
        );
    }

    @PostMapping("/search")
    public ApiResponse<PageResponse<EventFinanceResponse>> search(
            @RequestBody EventFinanceSearchRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Movimientos financieros obtenidos correctamente",
                eventFinanceService.search(request)
        );
    }

    @GetMapping("/find/{id}")
    public ApiResponse<EventFinanceResponse> getById(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                200,
                "Movimiento financiero obtenido correctamente",
                eventFinanceService.getById(id)
        );
    }
}
