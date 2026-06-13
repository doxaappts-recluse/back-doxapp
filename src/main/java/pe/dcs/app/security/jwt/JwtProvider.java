package pe.dcs.app.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import pe.dcs.app.constant.GeneralConstant;
import pe.dcs.app.security.payload.JwtTimesResponse;
import pe.dcs.app.security.service.credentials.CredentialDetailsImpl;
import pe.dcs.app.util.CryptoUtils;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Component
public class JwtProvider {

    private static final Logger logger = LogManager.getLogger(JwtProvider.class);

    private static final String INICIO_TITULO_GENERATE_JWT_METHOD = "-- Inicio de Método: Generar Pre-JWT --";
    private static final String FIN_TITULO_GENERATE_JWT_METHOD = "-- Fin de Método: Generar Pre-JWT --";
    private static final String INICIO_TITULO_VALIDATE_JWT_METHOD = "-- Inicio de Método: Validación de JWT --";
    private static final String FIN_TITULO_VALIDATE_JWT_METHOD = "-- Fin de Método: Validación de JWT --";
    private static final String INICIO_TITULO_GET_USERNAME_FROM_JWT_METHOD = "-- Inicio de Método: Obtener Usuario del JWT --";
    private static final String FIN_TITULO_GET_USERNAME_FROM_JWT_METHOD = "-- Fin de Método: Obtener Usuario del JWT --";
    private static final String INICIO_TITULO_GET_DATES_FROM_JWT_METHOD = "-- Inicio de Método: Obtener Fechas del JWT --";
    private static final String FIN_TITULO_GET_DATES_FROM_JWT_METHOD = "-- Fin de Método: Obtener Fechas del JWT --";

    @Value("#{${app.jwt.time-in-minutes} * 1440000}")
    private int expiracionToken;

    @Value("${app.jwt.secret}")
    private String secret;

    public String generateJWT(Authentication authentication) {

        logger.info(INICIO_TITULO_GENERATE_JWT_METHOD);

        logger.info("Iniciando la generación del JWT");

        CredentialDetailsImpl usuarioPrincipal = (CredentialDetailsImpl) authentication.getPrincipal();

        logger.info("Secret Encontrado: {}", secret);

        logger.info("Encodeando en Base64 el secret del JWT: {}", secret);
        String secretEncoded = CryptoUtils.encodeSecretBase64(secret);

        logger.info("Secret Encodeado en Base64: {} para usarlo como firma del JWT", secretEncoded);

        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretEncoded));

        String jwt = Jwts.builder()
                .setSubject(usuarioPrincipal.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(new Date().getTime() + expiracionToken))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        logger.info("Se generó el JWT: {}", jwt);

        logger.info(FIN_TITULO_GENERATE_JWT_METHOD);

        return jwt;
    }

    public boolean validateJWT(String token) {

        logger.info(INICIO_TITULO_VALIDATE_JWT_METHOD);

        String secretEncoded = CryptoUtils.encodeSecretBase64(secret);
        logger.info("Secret Encodeado en Base64 para Validacion de JWT: {}", secretEncoded);

        try {
            Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(secretEncoded)))
                    .build()
                    .parseClaimsJws(token);

            logger.info(FIN_TITULO_VALIDATE_JWT_METHOD);

            return true;
        } catch (MalformedJwtException e) {
            logger.error("Token JWT Malformado al Validar JWT: {}", e.getMessage());

            logger.info(FIN_TITULO_VALIDATE_JWT_METHOD);

            return false;
        } catch (ExpiredJwtException e) {
            logger.error("Token JWT Expirado al Validar JWT: {}", e.getMessage());

            logger.info(FIN_TITULO_VALIDATE_JWT_METHOD);

            return false;
        } catch (UnsupportedJwtException e) {
            logger.error("Token JWT No Soportado al Validar JWT: {}", e.getMessage());

            logger.info(FIN_TITULO_VALIDATE_JWT_METHOD);

            return false;
        } catch (IllegalArgumentException e) {
            logger.error("Token JWT Vacío al Validar JWT: {}", e.getMessage());

            logger.info(FIN_TITULO_VALIDATE_JWT_METHOD);

            return false;
        } catch (io.jsonwebtoken.security.SignatureException e) {
            logger.error("Token con Firma no Valida al Validar JWT: {}", e.getMessage());

            logger.info(FIN_TITULO_VALIDATE_JWT_METHOD);

            return false;
        }
    }

    public String getUsernameFromToken(String token) {

        logger.info(INICIO_TITULO_GET_USERNAME_FROM_JWT_METHOD);

        String secretEncoded = CryptoUtils.encodeSecretBase64(secret);
        logger.info("Secret Encodeado en Base64 para obtener Username del JWT: {}", secretEncoded);

        try {
            JwtParser jwtParser = Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(secretEncoded)))
                    .build();

            Jwt<?, Claims> jwt = jwtParser.parseClaimsJws(token);

            Claims claims = jwt.getBody();

            String username = claims.getSubject();

            logger.info("Usuario del JWT: {}", username);

            logger.info(FIN_TITULO_GET_USERNAME_FROM_JWT_METHOD);

            return username;
        } catch (MalformedJwtException e) {
            logger.error("Token JWT Malformado al obtener Fechas: {}", e.getMessage());

            logger.info(FIN_TITULO_GET_DATES_FROM_JWT_METHOD);

            return null;
        } catch (ExpiredJwtException e) {
            logger.error("Token JWT Expirado al obtener Fechas: {}", e.getMessage());

            logger.info(FIN_TITULO_GET_DATES_FROM_JWT_METHOD);

            return null;
        } catch (UnsupportedJwtException e) {
            logger.error("Token JWT No Soportado al obtener Fechas: {}", e.getMessage());

            logger.info(FIN_TITULO_GET_DATES_FROM_JWT_METHOD);

            return null;
        } catch (IllegalArgumentException e) {
            logger.error("Token JWT Vacío al obtener Fechas: {}", e.getMessage());

            logger.info(FIN_TITULO_GET_DATES_FROM_JWT_METHOD);

            return null;
        } catch (io.jsonwebtoken.security.SignatureException e) {
            logger.error("Token con Firma no Valida al obtener Fechas: {}", e.getMessage());

            logger.info(FIN_TITULO_GET_DATES_FROM_JWT_METHOD);

            return null;
        }
    }

    public JwtTimesResponse getTimesFromJWT(String token) {

        logger.info(INICIO_TITULO_GET_DATES_FROM_JWT_METHOD);

        String secretEncoded = CryptoUtils.encodeSecretBase64(secret);
        logger.info("Secret Encodeado en Base64 para obtener fechas del JWT: {}", secretEncoded);

        try {
            JwtParser jwtParser = Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(secretEncoded)))
                    .build();

            Jwt<?, Claims> jwt = jwtParser.parseClaimsJws(token);

            Claims claims = jwt.getBody();

            Date iatDate = claims.getIssuedAt();
            Date expDate = claims.getExpiration();

            logger.info("Fecha de Emisión JWT: {}", iatDate);
            logger.info("Fecha de Expiración JWT: {}", expDate);

            Instant iatInstant = iatDate.toInstant();
            Instant expInstant = expDate.toInstant();

            LocalDateTime localdtEmision = iatInstant.atZone(GeneralConstant.ID_ZONA_LOCAL).toLocalDateTime();
            LocalDateTime localdtExpiracion = expInstant.atZone(GeneralConstant.ID_ZONA_LOCAL).toLocalDateTime();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(GeneralConstant.PATTERN_FECHA_COMPLETA_GUION);

            String fechaEmision = localdtEmision.format(formatter);
            String fechaExpiracion = localdtExpiracion.format(formatter);

            logger.info("Fecha de Emisión JWT Formateado: {}", fechaEmision);
            logger.info("Fecha de Expiración JWT Formateado: {}", fechaExpiracion);

            logger.info(FIN_TITULO_GET_DATES_FROM_JWT_METHOD);

            return new JwtTimesResponse(fechaEmision, fechaExpiracion);
        } catch (MalformedJwtException e) {
            logger.error("Token JWT Malformado al obtener Fechas: {}", e.getMessage());

            logger.info(FIN_TITULO_GET_DATES_FROM_JWT_METHOD);

            return null;
        } catch (ExpiredJwtException e) {
            logger.error("Token JWT Expirado al obtener Fechas: {}", e.getMessage());

            logger.info(FIN_TITULO_GET_DATES_FROM_JWT_METHOD);

            return null;
        } catch (UnsupportedJwtException e) {
            logger.error("Token JWT No Soportado al obtener Fechas: {}", e.getMessage());

            logger.info(FIN_TITULO_GET_DATES_FROM_JWT_METHOD);

            return null;
        } catch (IllegalArgumentException e) {
            logger.error("Token JWT Vacío al obtener Fechas: {}", e.getMessage());

            logger.info(FIN_TITULO_GET_DATES_FROM_JWT_METHOD);

            return null;
        } catch (SignatureException e) {
            logger.error("Token con Firma no Valida al obtener Fechas: {}", e.getMessage());

            logger.info(FIN_TITULO_GET_DATES_FROM_JWT_METHOD);

            return null;
        }
    }
}