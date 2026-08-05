package pe.dcs.app.features.finance;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.finance.request.FinancialFundRequest;
import pe.dcs.app.features.finance.response.FinancialFundResponse;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/financial-funds")
@RequiredArgsConstructor
public class FinancialFundController {

    private final FinancialFundService financialFundService;

    @PostMapping("/create")
    public ApiResponse<FinancialFundResponse> create(
            @Valid @RequestBody FinancialFundRequest request
    ) {

        return new ApiResponse<>(
                200,
                "success.fondoCreadoCorrectamente",
                financialFundService.create(request)
        );
    }

    @PutMapping("/update/{id}")
    public ApiResponse<FinancialFundResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody FinancialFundRequest request
    ) {

        return new ApiResponse<>(
                200,
                "success.fondoActualizadoCorrectamente",
                financialFundService.update(id, request)
        );
    }

    @PatchMapping("/enable/{id}")
    public ApiResponse<FinancialFundResponse> enable(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                200,
                "success.fondoHabilitadoCorrectamente",
                financialFundService.enable(id)
        );
    }

    @PatchMapping("/disable/{id}")
    public ApiResponse<FinancialFundResponse> disable(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                200,
                "success.fondoInhabilitadoCorrectamente",
                financialFundService.disable(id)
        );
    }

    @GetMapping("/get/{id}")
    public ApiResponse<FinancialFundResponse> getById(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                200,
                "success.fondoObtenidoCorrectamente",
                financialFundService.getById(id)
        );
    }

    @GetMapping("/active")
    public ApiResponse<List<FinancialFundResponse>> listActive() {

        return new ApiResponse<>(
                200,
                "success.fondosActivosObtenidosCorrectamente",
                financialFundService.listActive()
        );
    }

    @GetMapping("/all")
    public ApiResponse<List<FinancialFundResponse>> listAll() {

        return new ApiResponse<>(
                200,
                "success.fondosObtenidosCorrectamente",
                financialFundService.listAll()
        );
    }
}
