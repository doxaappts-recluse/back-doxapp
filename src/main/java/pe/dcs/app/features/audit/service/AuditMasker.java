package pe.dcs.app.features.audit.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * M22 V14 · el diff no muestra campos sensibles (sueldos, notas pastorales…) a quien no tiene la acción H del módulo dueño.
 * Las claves y los módulos "totalmente sensibles" se configuran ({@code audit.sensitive-keys}, {@code audit.sensitive-modules}).
 */
@Component
public class AuditMasker {

    public static final String MASK = "***";

    private final Set<String> keys;
    private final Set<String> modules;

    public AuditMasker(
            @Value("${audit.sensitive-keys:salary,basesalary,sueldo,netpay,grosspay,pastoralnote,pastoralnotes,diagnosis,healthnote,prayerrequest}") String keys,
            @Value("${audit.sensitive-modules:PASTORAL_CARE,PRAYER,HR_PAYROLL}") String modules) {
        this.keys = Arrays.stream(keys.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        this.modules = Arrays.stream(modules.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    /** Resultado del enmascarado: el diff a mostrar y si se ocultó algo. */
    public record Masked(Map<String, Object> diff, boolean masked) {
    }

    public Masked apply(Map<String, Object> diff, String moduleCode, boolean canSeeSensitive) {
        if (diff == null || canSeeSensitive) {
            return new Masked(diff, false);
        }
        boolean whole = modules.contains(moduleCode);
        boolean[] hit = {false};
        Map<String, Object> out = maskMap(diff, whole, hit);
        return new Masked(out, hit[0]);
    }

    private Map<String, Object> maskMap(Map<String, Object> in, boolean whole, boolean[] hit) {
        Map<String, Object> out = new LinkedHashMap<>();
        in.forEach((k, v) -> {
            if (whole || keys.contains(k.toLowerCase(Locale.ROOT))) {
                out.put(k, MASK);
                hit[0] = true;
            } else {
                out.put(k, maskValue(v, hit));
            }
        });
        return out;
    }

    @SuppressWarnings("unchecked")
    private Object maskValue(Object v, boolean[] hit) {
        if (v instanceof Map<?, ?> m) {
            return maskMap((Map<String, Object>) m, false, hit);
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            l.forEach(e -> out.add(maskValue(e, hit)));
            return out;
        }
        return v;
    }
}
