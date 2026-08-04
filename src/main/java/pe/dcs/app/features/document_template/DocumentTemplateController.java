package pe.dcs.app.features.document_template;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.dcs.app.features.document_template.request.DocumentTemplateRequest;
import pe.dcs.app.features.document_template.response.DocumentTemplateDownloadResponse;
import pe.dcs.app.features.document_template.response.DocumentTemplateResponse;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.enums.DocumentTemplateType;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/document-templates")
@RequiredArgsConstructor
public class DocumentTemplateController {

    private final DocumentTemplateService documentTemplateService;

    @PostMapping("/create")
    public ApiResponse<DocumentTemplateResponse> create(
            @Valid @RequestPart("documentTemplate") DocumentTemplateRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {

        return new ApiResponse<>(
                200,
                "Plantilla creada correctamente",
                documentTemplateService.create(request, file)
        );
    }

    @PutMapping("/update/{id}")
    public ApiResponse<DocumentTemplateResponse> update(
            @PathVariable UUID id,
            @Valid @RequestPart("documentTemplate") DocumentTemplateRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {

        return new ApiResponse<>(
                200,
                "Plantilla actualizada correctamente",
                documentTemplateService.update(id, request, file)
        );
    }

    @PatchMapping("/enable/{id}")
    public ApiResponse<DocumentTemplateResponse> enable(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                200,
                "Plantilla habilitada correctamente",
                documentTemplateService.enable(id)
        );
    }

    @PatchMapping("/disable/{id}")
    public ApiResponse<DocumentTemplateResponse> disable(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                200,
                "Plantilla inhabilitada correctamente",
                documentTemplateService.disable(id)
        );
    }

    @GetMapping("/get/{id}")
    public ApiResponse<DocumentTemplateResponse> getById(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                200,
                "Plantilla obtenida correctamente",
                documentTemplateService.getById(id)
        );
    }

    @GetMapping("/all")
    public ApiResponse<List<DocumentTemplateResponse>> listAll() {

        return new ApiResponse<>(
                200,
                "Plantillas obtenidas correctamente",
                documentTemplateService.listAll()
        );
    }

    /**
     * Endpoint consumidor: entrega solo la URL firmada de la
     * plantilla RAW + su config — el front pinta los datos
     * (nombre/fecha/monto) client-side. No requiere assertCanManage
     * porque cualquier usuario con acceso al módulo consumidor
     * (p.ej. detalle de Donante) necesita poder descargar.
     */
    @GetMapping("/download/{documentType}")
    public ApiResponse<DocumentTemplateDownloadResponse> download(
            @PathVariable DocumentTemplateType documentType
    ) {

        return new ApiResponse<>(
                200,
                "Plantilla obtenida correctamente",
                documentTemplateService.download(documentType)
        );
    }
}
