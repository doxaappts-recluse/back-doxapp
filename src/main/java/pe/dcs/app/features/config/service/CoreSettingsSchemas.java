package pe.dcs.app.features.config.service;

import org.springframework.stereotype.Component;

import java.util.List;

/** Esquemas de los módulos ya construidos. */
@Component
public class CoreSettingsSchemas implements SettingsSchemaProvider {

    @Override
    public List<SettingsSchema> schemas() {
        return List.of(
                new SettingsSchema("ACCESS", "SUPPORT_TEAM", "Accesos y equipo", "Access and team", List.of(
                        SettingKeyDef.integer("invitationValidityHours", 72, 1, 168, false,
                                "Vigencia de las invitaciones (horas)", "Invitation validity (hours)",
                                "Tiempo que el enlace de invitación sigue sirviendo. Máximo 168 h (7 días).",
                                "How long the invitation link stays valid. Maximum 168 h (7 days)."))),
                new SettingsSchema("BRANCH", "BRANCH", "Sedes", "Branches", List.of(
                        SettingKeyDef.integer("publicScheduleMax", 30, 1, 60, true,
                                "Máximo de horarios públicos por sede", "Maximum public schedule rows per branch",
                                "Cuántos horarios de culto puede publicar cada sede. Cada sede puede tener su propio valor.",
                                "How many service times each branch can publish. Each branch can have its own value."))));
    }
}
