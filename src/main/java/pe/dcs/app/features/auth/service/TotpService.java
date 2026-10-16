package pe.dcs.app.features.auth.service;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** MFA TOTP (RFC 6238): SHA-1, 6 dígitos, 30 s, tolerancia de ±1 ventana. */
@Component
public class TotpService {

    static final String ISSUER = "DoxApp";

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator(32);
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
    private final CodeVerifier verifier;

    public TotpService(Clock clock) {
        TimeProvider time = () -> clock.instant().getEpochSecond();
        DefaultCodeVerifier v = new DefaultCodeVerifier(codeGenerator, time);
        v.setTimePeriod(30);
        v.setAllowedTimePeriodDiscrepancy(1);
        this.verifier = v;
    }

    public String newSecret() {
        return secretGenerator.generate();
    }

    /** URI otpauth:// que la app autenticadora lee (el front la muestra como QR). */
    public String otpAuthUri(String account, String secret) {
        return new QrData.Builder()
                .label(account)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build()
                .getUri();
    }

    public boolean verify(String secret, String code) {
        return code != null && code.matches("\\d{6}") && verifier.isValidCode(secret, code);
    }
}
