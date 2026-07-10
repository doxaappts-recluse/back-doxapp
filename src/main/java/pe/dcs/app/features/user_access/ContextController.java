package pe.dcs.app.features.user_access;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.user_access.response.ContextBranchResponse;
import pe.dcs.app.features.user_access.service.ContextService;
import pe.dcs.app.security.service.credentials.CredentialDetailsImpl;
import pe.dcs.app.util.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/api/v1/context")
@RequiredArgsConstructor
public class ContextController {

    private final ContextService contextService;

    @GetMapping("/available")
    public ApiResponse<List<ContextBranchResponse>> getAvailableContexts(
            Authentication authentication
    ){

        CredentialDetailsImpl principal =
                (CredentialDetailsImpl)
                        authentication.getPrincipal();

        List<ContextBranchResponse> response =
                contextService.getAvailableContexts(
                        principal.getUserId()
                );

        return  new ApiResponse<>(
                        200,
                        "Contextos disponibles",
                        response
                );

    }

}