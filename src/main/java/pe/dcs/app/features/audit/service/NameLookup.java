package pe.dcs.app.features.audit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Nombres a mostrar para ids de personas, personal de plataforma, organizaciones y sedes (consultas por lote). */
@Component
@RequiredArgsConstructor
public class NameLookup {

    private final NamedParameterJdbcTemplate jdbc;

    public Map<UUID, String> people(Collection<UUID> ids) {
        return lookup("select id, first_name || ' ' || last_name from person where id in (:ids)", ids);
    }

    public Map<UUID, String> staff(Collection<UUID> ids) {
        return lookup("select id, first_name || ' ' || last_name from platform_staff where id in (:ids)", ids);
    }

    public Map<UUID, String> organizations(Collection<UUID> ids) {
        return lookup("select id, name from organization where id in (:ids)", ids);
    }

    public Map<UUID, String> branches(Collection<UUID> ids) {
        return lookup("select id, name from branch where id in (:ids)", ids);
    }

    private Map<UUID, String> lookup(String sql, Collection<UUID> ids) {
        Set<UUID> set = ids.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> out = new HashMap<>();
        if (set.isEmpty()) {
            return out;
        }
        jdbc.query(sql, new MapSqlParameterSource("ids", set), rs -> {
            out.put(rs.getObject(1, UUID.class), rs.getString(2));
        });
        return out;
    }
}
