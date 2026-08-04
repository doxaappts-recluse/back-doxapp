package pe.dcs.app.features.finance;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.finance.request.FinancialMovementApproveRequest;
import pe.dcs.app.features.finance.request.FinancialMovementFilter;
import pe.dcs.app.features.finance.request.FinancialMovementRejectRequest;
import pe.dcs.app.features.finance.request.FinancialMovementRequest;
import pe.dcs.app.features.finance.request.FinancialMovementSearchRequest;
import pe.dcs.app.features.finance.response.FinancialDonorResponse;
import pe.dcs.app.features.finance.response.FinancialMovementResponse;
import pe.dcs.app.features.finance.response.FinancialMovementSummaryResponse;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/financial-movements")
@RequiredArgsConstructor
public class FinancialMovementController {

    private final FinancialMovementService financialMovementService;

    @PostMapping("/create")
    public ApiResponse<FinancialMovementResponse> create(
            @RequestBody FinancialMovementRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Movimiento financiero creado correctamente",
                financialMovementService.create(request)
        );
    }

    @PutMapping("/update/{id}")
    public ApiResponse<FinancialMovementResponse> update(
            @PathVariable UUID id,
            @RequestBody FinancialMovementRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Movimiento financiero actualizado correctamente",
                financialMovementService.update(id, request)
        );
    }

    @PatchMapping("/approve/{id}")
    public ApiResponse<FinancialMovementResponse> approve(
            @PathVariable UUID id,
            @RequestBody(required = false)
                    FinancialMovementApproveRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Movimiento financiero aprobado correctamente",
                financialMovementService.approve(id, request)
        );
    }

    @PatchMapping("/reject/{id}")
    public ApiResponse<FinancialMovementResponse> reject(
            @PathVariable UUID id,
            @RequestBody FinancialMovementRejectRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Movimiento financiero rechazado correctamente",
                financialMovementService.reject(id, request)
        );
    }

    @PostMapping("/search")
    public ApiResponse<PageResponse<FinancialMovementResponse>> search(
            @RequestBody FinancialMovementSearchRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Movimientos financieros obtenidos correctamente",
                financialMovementService.search(request)
        );
    }

    @PostMapping("/summary")
    public ApiResponse<FinancialMovementSummaryResponse> summary(
            @RequestBody(required = false)
                    FinancialMovementFilter filters
    ) {

        return new ApiResponse<>(
                200,
                "Resumen de movimientos financieros obtenido correctamente",
                financialMovementService.summary(filters)
        );
    }

    @PostMapping("/donors")
    public ApiResponse<List<FinancialDonorResponse>> donors(
            @RequestBody(required = false)
                    FinancialMovementFilter filters
    ) {

        return new ApiResponse<>(
                200,
                "Donantes obtenidos correctamente",
                financialMovementService.donors(filters)
        );
    }

    @GetMapping("/find/{id}")
    public ApiResponse<FinancialMovementResponse> getById(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                200,
                "Movimiento financiero obtenido correctamente",
                financialMovementService.getById(id)
        );
    }
}
