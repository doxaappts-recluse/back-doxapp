package pe.dcs.app.features.catalog.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Tipos de catálogo conocidos. Cada módulo posterior registra aquí los suyos (no crea tablas de "tipos" propias). */
@Component
public class CatalogTypeRegistry {

    private final Map<String, CatalogTypeDef> types = new LinkedHashMap<>();

    public CatalogTypeRegistry() {
        add("DOCUMENT_TYPE", "PERSON", false, false, null, true, true);
        add("GENDER", "PERSON", false, false, null, false, true);
        add("MARITAL_STATUS", "PERSON", false, false, null, false, true);
        add("RELATIONSHIP", "PERSON", true, true, null, false, false);
        add("PROFESSION", "PERSON", true, true, null, false, false);
        add("LANGUAGE", "ORG_SETTINGS", false, false, null, true, true);
        add("COUNTRY", "ORG_SETTINGS", false, false, null, true, true);
        add("REGION", "ORG_SETTINGS", true, true, "COUNTRY", false, false);
        add("DISTRICT", "ORG_SETTINGS", true, true, "REGION", false, false);
        add("RITE_TYPE", "MEMBERSHIP", true, true, null, false, false);
        add("FINANCE_CATEGORY", "FINANCE", true, true, null, false, true);
        add("PAYMENT_METHOD", "FINANCE", true, true, null, false, true);
        add("SPACE_TYPE", "FACILITIES", true, true, null, false, false);
        add("GROUP_TYPE", "GROUPS", true, true, null, false, false);
        add("GROUP_CATEGORY", "SMALL_GROUP", true, true, null, false, false);
        add("SCREENING_TYPE", "MINISTRY", true, true, null, false, false);
        add("EXIT_REASON", "MEMBERSHIP", true, true, null, false, false);
        add("VISITOR_SOURCE", "VISITORS", true, true, null, false, false);
        add("VISITOR_ARCHIVE_REASON", "VISITORS", true, true, null, false, false);
        add("PRAYER_CATEGORY", "PRAYER", true, true, null, false, false);
        add("CASE_RESULT", "PASTORAL_CARE", true, true, null, false, false);
        add("PASTORAL_CARE_TYPE", "PASTORAL", true, true, null, false, false);
        add("SERVICE_TYPE", "ATTENDANCE", true, true, null, false, false);
        add("ABSENCE_TYPE", "HR", true, true, null, false, false);
        add("EVENT_TYPE", "EVENTS", true, true, null, false, false);
        add("INVENTORY_CATEGORY", "INVENTORY", true, true, null, false, false);
    }

    private void add(String code, String owner, boolean editable, boolean extensible, String parent, boolean pub, boolean mandatory) {
        types.put(code, new CatalogTypeDef(code, owner, editable, extensible, parent, pub, mandatory));
    }

    public List<CatalogTypeDef> all() {
        return List.copyOf(types.values());
    }

    public Optional<CatalogTypeDef> find(String code) {
        return Optional.ofNullable(code == null ? null : types.get(code.trim().toUpperCase()));
    }
}
