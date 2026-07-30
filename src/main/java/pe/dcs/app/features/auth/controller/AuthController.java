package pe.dcs.app.features.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.auth.request.ContextRequest;
import pe.dcs.app.features.auth.request.LoginRequest;
import pe.dcs.app.features.auth.response.ContextResponse;
import pe.dcs.app.features.auth.response.JwtResponse;
import pe.dcs.app.features.auth.service.AuthService;
import pe.dcs.app.security.jwt.JwtProvider;
import pe.dcs.app.security.payload.JwtTimesResponse;
import pe.dcs.app.security.service.credentials.CredentialDetailsImpl;
import pe.dcs.app.util.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtResponse>> login(
            @RequestBody LoginRequest request
    ) {

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.getUsername(),
                                request.getPassword()
                        )
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        CredentialDetailsImpl principal =
                (CredentialDetailsImpl)
                        authentication.getPrincipal();

        String token =
                jwtProvider.generateLoginToken(
                        principal
                );

        JwtTimesResponse times =
                jwtProvider.getTimesFromJWT(
                        token
                );

        List<String> roles =
                principal.getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .distinct()
                        .toList();

        JwtResponse response =
                new JwtResponse(
                        token,
                        principal.getUserId(),
                        principal.getUsername(),
                        principal.getName(),
                        principal.getLastname(),
                        roles,
                        principal.getAccesses(),
                        principal.getCurrentOrganizationId(),
                        principal.getCurrentBranchId(),
                        times.issuedAt(),
                        times.expiration()
                );

        ResponseEntity.BodyBuilder responseBuilder =
                ResponseEntity.ok();

        /*
         * Login válido (credenciales correctas), pero la persona no
         * tiene NINGÚN UserAccess activo (p.ej. a un ORG_ADMIN o
         * ORG_BRANCH_ADMIN se le deshabilitó su único acceso). El
         * front no debe navegar como si todo estuviera normal: este
         * header le avisa para mostrar una notificación en vez de
         * quedar en un estado vacío/roto en silencio.
         */
        if (principal.getAccesses().isEmpty()) {

            responseBuilder.header(
                    "X-No-Access",
                    "true"
            );
        }

        return responseBuilder.body(
                new ApiResponse<>(
                        200,
                        "Login exitoso",
                        response
                )
        );
    }

    @PostMapping("/context")
    public ApiResponse<ContextResponse> changeContext(
            @Valid
            @RequestBody ContextRequest request,
            Authentication authentication
    ) {

        CredentialDetailsImpl user =
                (CredentialDetailsImpl)
                        authentication.getPrincipal();

        String token =
                authService.changeContext(
                        user,
                        request.organizationId(),
                        request.branchId()
                );

        return new ApiResponse<>(
                        200,
                        "Context changed.",
                        new ContextResponse(token)
        );

    }
}