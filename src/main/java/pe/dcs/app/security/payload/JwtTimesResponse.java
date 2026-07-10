package pe.dcs.app.security.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

public record JwtTimesResponse(
        Instant issuedAt,
        Instant expiration
){}