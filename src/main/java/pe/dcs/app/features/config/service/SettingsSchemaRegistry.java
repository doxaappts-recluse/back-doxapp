package pe.dcs.app.features.config.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class SettingsSchemaRegistry {

    private final Map<String, SettingsSchema> schemas = new LinkedHashMap<>();

    public SettingsSchemaRegistry(ObjectProvider<SettingsSchemaProvider> providers) {
        providers.orderedStream().forEach(p -> p.schemas().forEach(s -> schemas.put(s.namespace(), s)));
    }

    public List<SettingsSchema> all() {
        return List.copyOf(schemas.values());
    }

    public Optional<SettingsSchema> find(String namespace) {
        return Optional.ofNullable(namespace == null ? null : schemas.get(namespace.trim().toUpperCase()));
    }
}
