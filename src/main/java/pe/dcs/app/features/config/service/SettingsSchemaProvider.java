package pe.dcs.app.features.config.service;

import java.util.List;

/** Cada módulo registra el esquema de sus ajustes con un bean de este tipo (las XRules son vistas tipadas de ellos). */
public interface SettingsSchemaProvider {
    List<SettingsSchema> schemas();
}
