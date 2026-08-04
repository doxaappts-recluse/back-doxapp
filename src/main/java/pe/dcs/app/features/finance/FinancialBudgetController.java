package pe.dcs.app.features.finance;

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
            @RequestBody FinancialBudgetRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Presupuesto creado correctamente",
                financialBudgetService.create(request)
        );
    }

    @PutMapping("/update/{id}")
    public ApiResponse<FinancialBudgetResponse> update(
            @PathVariable UUID id,
            @RequestBody FinancialBudgetRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Presupuesto actualizado correctamente",
                financialBudgetService.update(id, request)
        );
    }

    @PatchMapping("/enable/{id}")
    public ApiResponse<FinancialBudgetResponse> enable(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                200,
                "Presupuesto habilitado correctamente",
                financialBudgetService.enable(id)
        );
    }

    @PatchMapping("/disable/{id}")
    public ApiResponse<FinancialBudgetResponse> disable(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                200,
                "Presupuesto inhabilitado correctamente",
                financialBudgetService.disable(id)
        );
    }

    @GetMapping("/get/{id}")
    public ApiResponse<FinancialBudgetResponse> getById(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                200,
                "Presupuesto obtenido correctamente",
                financialBudgetService.getById(id)
        );
    }

    @GetMapping("/all")
    public ApiResponse<List<FinancialBudgetResponse>> listAll() {

        return new ApiResponse<>(
                200,
                "Presupuestos obtenidos correctamente",
                financialBudgetService.listAll()
        );
    }

    @GetMapping("/{id}/progress")
    public ApiResponse<FinancialBudgetProgressResponse> progress(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                200,
                "Avance del presupuesto obtenido correctamente",
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
                "Avance de presupuestos obtenido correctamente",
                financialBudgetService.progressForPeriod(periodYear, periodMonth)
        );
    }
}
