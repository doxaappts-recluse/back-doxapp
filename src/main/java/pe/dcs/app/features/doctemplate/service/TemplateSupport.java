package pe.dcs.app.features.doctemplate.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.util.Exceptions;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** M18 · Catálogo de variables por tipo de documento [V1] y utilidades comunes de Plantillas y certificados (DOC_TEMPLATES). */
@Component
public class TemplateSupport {

    public static final String MODULE = "DOC_TEMPLATES";

    public static final Set<String> TYPES = Set.of("BAPTISM_CERTIFICATE", "MEMBERSHIP_CERTIFICATE", "MARRIAGE_CERTIFICATE",
            "CHILD_DEDICATION_CERTIFICATE", "BIBLE_ACADEMY_CERTIFICATE", "DONATION_CERTIFICATE", "EVENT_TICKET", "EVENT_PARTICIPATION",
            "RECEIPT", "PAYSLIP", "EMPLOYMENT_LETTER", "OTHER");

    /** Variables comunes a todo tipo de documento; `number` y `qr` las rellena el motor al emitir, nunca las da el usuario. */
    public static final Set<String> COMMON_VARIABLES = Set.of("org.displayName", "org.name", "branch.name", "number", "date", "qr");

    private static final Map<String, Set<String>> SPECIFIC = Map.ofEntries(
            Map.entry("BAPTISM_CERTIFICATE", Set.of("person.fullName", "rite.date", "officiant")),
            Map.entry("MEMBERSHIP_CERTIFICATE", Set.of("person.fullName", "rite.date", "officiant")),
            Map.entry("MARRIAGE_CERTIFICATE", Set.of("person.fullName", "person2.fullName", "rite.date", "officiant")),
            Map.entry("CHILD_DEDICATION_CERTIFICATE", Set.of("person.fullName", "rite.date", "officiant")),
            Map.entry("BIBLE_ACADEMY_CERTIFICATE", Set.of("person.fullName", "course.name", "course.hours")),
            Map.entry("DONATION_CERTIFICATE", Set.of("person.fullName", "amount", "year")),
            Map.entry("EVENT_TICKET", Set.of("person.fullName", "event.name", "event.date", "event.venue")),
            Map.entry("EVENT_PARTICIPATION", Set.of("person.fullName", "event.name", "event.date")),
            Map.entry("RECEIPT", Set.of("person.fullName", "amount", "concept", "date")),
            Map.entry("PAYSLIP", Set.of("person.fullName", "period", "netAmount")),
            Map.entry("EMPLOYMENT_LETTER", Set.of("person.fullName", "position", "hireDate")),
            Map.entry("OTHER", Set.of("person.fullName", "subject")));

    /** Variable "sujeto principal" que toda plantilla del tipo debe usar además de `number` [V4]. */
    private static final Map<String, String> PRIMARY = Map.ofEntries(
            Map.entry("BAPTISM_CERTIFICATE", "person.fullName"), Map.entry("MEMBERSHIP_CERTIFICATE", "person.fullName"),
            Map.entry("MARRIAGE_CERTIFICATE", "person.fullName"), Map.entry("CHILD_DEDICATION_CERTIFICATE", "person.fullName"),
            Map.entry("BIBLE_ACADEMY_CERTIFICATE", "person.fullName"), Map.entry("DONATION_CERTIFICATE", "person.fullName"),
            Map.entry("EVENT_TICKET", "person.fullName"), Map.entry("EVENT_PARTICIPATION", "person.fullName"),
            Map.entry("RECEIPT", "person.fullName"), Map.entry("PAYSLIP", "person.fullName"), Map.entry("EMPLOYMENT_LETTER", "person.fullName"),
            Map.entry("OTHER", "subject"));

    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*}}");
    private static final Pattern SCRIPT = Pattern.compile("(?is)<script[^>]*>.*?</script>");
    private static final Pattern ON_ATTR = Pattern.compile("(?i)\\son\\w+\\s*=\\s*\"[^\"]*\"");
    private static final Pattern JS_URL = Pattern.compile("(?i)javascript:");

    public static String type(String raw) {
        String t = raw == null ? null : raw.trim().toUpperCase();
        if (t == null || !TYPES.contains(t)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
        }
        return t;
    }

    public static TemplateDtos_VariableCatalog catalogOf(String type) {
        return new TemplateDtos_VariableCatalog(COMMON_VARIABLES, SPECIFIC.getOrDefault(type, Set.of()), PRIMARY.get(type));
    }

    public record TemplateDtos_VariableCatalog(Set<String> common, Set<String> specific, String primary) {
    }

    /** [V1][V4] extrae los tokens `{{...}}` del diseño y valida contra el catálogo del tipo; exige `number` y la variable principal. */
    @SuppressWarnings("unchecked")
    public static Set<String> extractTokens(Map<String, Object> design) {
        Set<String> tokens = new LinkedHashSet<>();
        if (design == null) {
            return tokens;
        }
        Object elementsObj = design.get("elements");
        if (elementsObj instanceof List<?> elements) {
            for (Object e : elements) {
                if (e instanceof Map<?, ?> el) {
                    Object content = el.get("content");
                    if (content instanceof String s) {
                        Matcher m = TOKEN.matcher(s);
                        while (m.find()) {
                            tokens.add(m.group(1));
                        }
                    }
                }
            }
        }
        return tokens;
    }

    public static void validateDesign(String type, Map<String, Object> design) {
        Set<String> tokens = extractTokens(design);
        TemplateDtos_VariableCatalog cat = catalogOf(type);
        Set<String> allowed = new LinkedHashSet<>(cat.common());
        allowed.addAll(cat.specific());
        List<String> unknown = tokens.stream().filter(t -> !allowed.contains(t)).sorted().toList();
        if (!unknown.isEmpty()) {
            throw new Exceptions("error.template.unknownVariables", HttpStatus.UNPROCESSABLE_ENTITY, String.join(", ", unknown));       // [V4]
        }
        List<String> missing = new java.util.ArrayList<>();
        if (!tokens.contains("number")) {
            missing.add("number");
        }
        if (cat.primary() != null && !tokens.contains(cat.primary())) {
            missing.add(cat.primary());
        }
        if (!missing.isEmpty()) {
            throw new Exceptions("error.template.missingVariables", HttpStatus.UNPROCESSABLE_ENTITY, String.join(", ", missing));       // [V4]
        }
    }

    // ---------------------------------------------------------------- imágenes [V5]
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/svg+xml", "image/webp");
    private static final long MAX_IMAGE = 1_048_576L;

    public static void validateImage(String contentType, long size) {
        if (contentType == null || !IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new Exceptions("error.template.imageInvalid", HttpStatus.BAD_REQUEST);
        }
        if (size > MAX_IMAGE) {
            throw new Exceptions("error.template.imageInvalid", HttpStatus.BAD_REQUEST);
        }
    }

    /** Saneo básico de SVG: fuera `<script>`, atributos `on*` y URLs `javascript:` — no es un parser XML completo, es defensa en profundidad. */
    public static byte[] sanitizeSvg(byte[] data) {
        String s = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        s = SCRIPT.matcher(s).replaceAll("");
        s = ON_ATTR.matcher(s).replaceAll("");
        s = JS_URL.matcher(s).replaceAll("");
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- alcance por sede

    public static String visibleByBranch(AccessScope scope, MapSqlParameterSource ps, String alias, boolean orgWideAllowed) {
        StringBuilder sb = new StringBuilder(alias + ".organization_id = :org");
        ps.addValue("org", scope.organizationId());
        if (!scope.allBranches()) {
            ps.addValue("scopeBranches", branchIdsOrNone(scope));
            sb.append(" and (").append(alias).append(".branch_id in (:scopeBranches)");
            if (orgWideAllowed) {
                sb.append(" or ").append(alias).append(".branch_id is null");
            }
            sb.append(")");
        }
        return sb.toString();
    }

    public static List<UUID> branchIdsOrNone(AccessScope scope) {
        return scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
    }

    public static String trim(String s, int max, String label) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.length() > max) {
            throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, label, max);
        }
        return t;
    }

    public static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
