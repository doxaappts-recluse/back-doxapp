package pe.dcs.app.features.finance;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.finance.request.FinancialCashRegisterCloseRequest;
import pe.dcs.app.features.finance.request.FinancialCashRegisterOpenRequest;
import pe.dcs.app.features.finance.response.FinancialCashRegisterResponse;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/financial-cash-registers")
@RequiredArgsConstructor
public class FinancialCashRegisterController {

    private final FinancialCashRegisterService financialCashRegisterService;

    @PostMapping("/open")
    public ApiResponse<FinancialCashRegisterResponse> open(
            @RequestBody FinancialCashRegisterOpenRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Caja abierta correctamente",
                financialCashRegisterService.open(request)
        );
    }

    @PatchMapping("/close/{id}")
    public ApiResponse<FinancialCashRegisterResponse> close(
            @PathVariable UUID id,
            @RequestBody FinancialCashRegisterCloseRequest request
    ) {

        return new ApiResponse<>(
                200,
                "Caja cerrada correctamente",
                financialCashRegisterService.close(id, request)
        );
    }

    @GetMapping("/get/{id}")
    public ApiResponse<FinancialCashRegisterResponse> getById(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                200,
                "Caja obtenida correctamente",
                financialCashRegisterService.getById(id)
        );
    }

    @GetMapping("/all")
    public ApiResponse<List<FinancialCashRegisterResponse>> listAll() {

        return new ApiResponse<>(
                200,
                "Cajas obtenidas correctamente",
                financialCashRegisterService.listAll()
        );
    }

    @GetMapping("/open-by-branch/{branchId}")
    public ApiResponse<FinancialCashRegisterResponse> getOpenByBranch(
            @PathVariable UUID branchId
    ) {

        return new ApiResponse<>(
                200,
                "Caja abierta obtenida correctamente",
                financialCashRegisterService.getOpenByBranch(branchId)
        );
    }
}
