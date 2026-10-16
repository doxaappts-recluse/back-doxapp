package pe.dcs.app.features.integration.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Comprueba en DNS que el dominio publica SPF ({@code v=spf1} en TXT) y la clave DKIM del selector
 * ({@code <selector>._domainkey.<dominio>} con {@code v=DKIM1}). {@code app.integrations.trusted-domains}
 * (lista separada por comas) declara dominios ya verificados: solo para desarrollo o revisión manual previa.
 */
@Component
public class DnsDomainVerifier implements DomainVerifier {

    private final Set<String> trusted;

    public DnsDomainVerifier(@Value("${app.integrations.trusted-domains:}") String trustedDomains) {
        this.trusted = Arrays.stream(trustedDomains.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    @Override
    public boolean verified(String domain, String dkimSelector) {
        String d = domain == null ? "" : domain.trim().toLowerCase(Locale.ROOT);
        if (d.isEmpty()) {
            return false;
        }
        if (trusted.contains(d)) {
            return true;
        }
        String selector = dkimSelector == null || dkimSelector.isBlank() ? "default" : dkimSelector.trim();
        return txt(d).stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains("v=spf1"))
                && txt(selector + "._domainkey." + d).stream().anyMatch(t -> t.toUpperCase(Locale.ROOT).contains("V=DKIM1"));
    }

    private static java.util.List<String> txt(String name) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", "2000");
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            DirContext ctx = new InitialDirContext(env);
            try {
                Attribute a = ctx.getAttributes(name, new String[]{"TXT"}).get("TXT");
                if (a != null) {
                    NamingEnumeration<?> en = a.getAll();
                    while (en.hasMore()) {
                        out.add(String.valueOf(en.next()));
                    }
                }
            } finally {
                ctx.close();
            }
        } catch (Exception e) {
            // sin DNS o sin registro: no verificado
        }
        return out;
    }
}
