package pe.dcs.app.features.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import pe.dcs.app.constant.ControllerConstant;
import pe.dcs.app.features.auth.response.JwtResponse;
import pe.dcs.app.features.auth.request.LoginRequest;
import pe.dcs.app.security.jwt.JwtProvider;
import pe.dcs.app.security.payload.JwtTimesResponse;
import pe.dcs.app.security.service.credentials.CredentialDetailsImpl;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.Exceptions;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtProvider jwtProvider
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtProvider = jwtProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtResponse>> login(@RequestBody LoginRequest loginRequest) {

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            String token = jwtProvider.generateJWT(authentication);

            CredentialDetailsImpl userDetails =
                    (CredentialDetailsImpl) authentication.getPrincipal();

            String username = userDetails.getUsername();
            UUID idusuario = userDetails.getId();
            String nombre = userDetails.getNombre();

            String rol = authentication.getAuthorities()
                    .stream()
                    .findFirst()
                    .map(GrantedAuthority::getAuthority)
                    .orElse(null);

            JwtTimesResponse fechas = jwtProvider.getTimesFromJWT(token);

            JwtResponse response = new JwtResponse(
                    token,
                    username,
                    nombre,
                    rol,
                    fechas.getEmision(),
                    fechas.getExpiracion()
            );

            return ResponseEntity.ok(
                    new ApiResponse<>(200, "Login exitoso", response)
            );

        } catch (BadCredentialsException e) {

            throw new Exceptions(
                    ControllerConstant.CREDENCIALES_INVALIDAS,
                    HttpStatus.BAD_REQUEST
            );

        } catch (Exception e) {

            throw new Exceptions(
                    ControllerConstant.ERROR_SERVER,
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}