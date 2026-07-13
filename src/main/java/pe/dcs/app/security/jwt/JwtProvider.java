package pe.dcs.app.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.dcs.app.util.constant.GeneralConstant;
import pe.dcs.app.security.payload.JwtTimesResponse;
import pe.dcs.app.security.service.credentials.CredentialDetailsImpl;
import pe.dcs.app.util.CryptoUtils;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {

    private static final Logger logger = LogManager.getLogger(JwtProvider.class);

    @Value("#{${app.jwt.time-in-minutes} * 1800000}")
    private long expiracionToken;

    @Value("${app.jwt.secret}")
    private String secret;

    private SecretKey getSigningKey() {

        String secretEncoded = CryptoUtils.encodeSecretBase64(secret);

        return Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(secretEncoded)
        );
    }

    private Claims parseClaims(String token){
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String generateLoginToken(
            CredentialDetailsImpl user
    ){

        return Jwts.builder()
                .setSubject(
                        user.getUsername()
                )
                .claim(
                        "type",
                        "LOGIN"
                )
                .setIssuedAt(
                        new Date()
                )
                .setExpiration(
                        new Date(
                                System.currentTimeMillis()
                                        + expiracionToken
                        )
                )
                .signWith(
                        getSigningKey(),
                        SignatureAlgorithm.HS256
                )
                .compact();
    }

    public String generateContextToken(
            CredentialDetailsImpl user,
            UUID organizationId,
            UUID branchId
    ){

        JwtBuilder builder =
                Jwts.builder()
                        .setSubject(
                                user.getUsername()
                        )
                        .claim(
                                "type",
                                "CONTEXT"
                        )
                        .setIssuedAt(
                                new Date()
                        )
                        .setExpiration(
                                new Date(
                                        System.currentTimeMillis()
                                                + expiracionToken
                                )
                        );

    /*
       SYSTEM_ADMIN

       organizationId = null
       branchId = null

       No agrega claims
    */

        if(organizationId != null){

            builder.claim(
                    "organizationId",
                    organizationId.toString()
            );

        }

        if(branchId != null){
            builder.claim(
                    "branchId",
                    branchId.toString()
            );

        }

        return builder
                .signWith(
                        getSigningKey(),
                        SignatureAlgorithm.HS256
                )
                .compact();

    }

    public boolean validateJWT(String token){

        try{

            parseClaims(token);

            return true;

        }catch (JwtException | IllegalArgumentException e){

            logger.error(
                    "JWT inválido: {}",
                    e.getMessage()
            );

            return false;
        }
    }

    public UUID getOrganizationId(String token){

        Claims claims = parseClaims(token);

        String value = claims.get(
                "organizationId",
                String.class
        );

        if(value == null){
            return null;
        }

        return UUID.fromString(value);
    }

    public UUID getBranchId(String token){

        Claims claims = parseClaims(token);

        String value = claims.get(
                "branchId",
                String.class
        );

        if(value == null){
            return null;
        }

        return UUID.fromString(value);
    }

    public String getUsername(String token) {
        return parseClaims(token)
                .getSubject();
    }

    public String getTokenType(
            String token
    ){

        return parseClaims(token)
                .get(
                        "type",
                        String.class
                );

    }

    public JwtTimesResponse getTimesFromJWT(String token){

        Claims claims = parseClaims(token);

        return new JwtTimesResponse(
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant()
        );
    }

}