package pe.dcs.app.features.auth.response;

import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

@Getter
@Setter
public class SigninResponse {

    //Atributos
    private String accessToken;
    private String emisionToken;
    private String expiracionToken;
    private String username;
    private Collection<? extends GrantedAuthority> authorities;

    //Constructores
    public SigninResponse(String accessToken, String emisionToken, String expiracionToken, String username, Collection<? extends GrantedAuthority> authorities) {
        this.accessToken = accessToken;
        this.emisionToken = emisionToken;
        this.expiracionToken = expiracionToken;
        this.username = username;
        this.authorities = authorities;
    }
}
