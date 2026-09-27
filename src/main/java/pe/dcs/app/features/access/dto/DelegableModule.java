package pe.dcs.app.features.access.dto;

import java.util.List;

/** Módulo que quien delega puede otorgar: delegable, del nivel de sede, contratado y que él mismo posee. */
public record DelegableModule(String code, String nameEs, String nameEn, List<String> actions) {
}
