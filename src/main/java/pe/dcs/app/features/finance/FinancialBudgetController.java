package pe.dcs.app.features.finance;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.finance.request.FinancialBudgetRequest;
import pe.dcs.app.features.finance.response.FinancialBudgetProgressResponse;
import pe.dcs.app.features.finance.response.FinancialBudgetResponse;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/financial-budgets")
@RequiredArgsConstructor
public class FinancialBudgetController {

    private final FinancialBudgetService financialBudgetService;

    @PostMapping("/create")
    public ApiResponse<FinancialBudgetResponse> create(
            @Valid @RequestBody FinancialBudgetRequest request
    ) {

        return new ApiResponse<>(
                200,
                "success.presupuestoCreadoCorrectamente",
                financialBudgetService.create(request)
        );
    }

    @PutMapping("/update/{id}")
    public ApiResponse<FinancialBudgetResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody FinancialBudgetRequest request
    ) {

        return new ApiResponse<>(
                200,
                "success.presupuestoActualizadoCorrectamente",
                financialBudgetService.update(id, request)
        );
    }

    @PatchMapping("/enable/{id}")
    public ApiResponse<FinancialBudgetResponse> enable(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                200,
                "success.presupuestoHabilitadoCorrectamente",
                financialBudgetService.enable(id)
        );
    }

    @PatchMapping("/disable/{id}")
    public ApiResponse<FinancialBudgetResponse> disable(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                200,
                "success.presupuestoInhabilitadoCorrectamente",
                financialBudgetService.disable(id)
        );
    }

    @GetMapping("/get/{id}")
    public ApiResponse<FinancialBudgetResponse> getById(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                200,
                "success.presupuestoObtenidoCorrectamente",
                financialBudgetService.getById(id)
        );
    }

    @GetMapping("/all")
    public ApiResponse<List<FinancialBudgetResponse>> listAll() {

        return new ApiResponse<>(
                200,
                "success.presupuestosObtenidosCorrectamente",
                financialBudgetService.listAll()
        );
    }

    @GetMapping("/{id}/progress")
    public ApiResponse<FinancialBudgetProgressResponse> progress(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                200,
                "success.avancePresupuestoObtenidoCorrectamente",
                financialBudgetService.progress(id)
        );
    }

    @GetMapping("/progress")
    public ApiResponse<List<FinancialBudgetProgressResponse>> progressForPeriod(
            @RequestParam Integer periodYear,
            @RequestParam Integer periodMonth
    ) {

        return new ApiResponse<>(
                200,
                "success.avancePresupuestosObtenidoCorrectamente",
                financialBudgetService.progressForPeriod(periodYear, periodMonth)
        );
    }
}
