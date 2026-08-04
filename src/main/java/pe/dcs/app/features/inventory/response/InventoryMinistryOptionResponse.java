package pe.dcs.app.features.inventory.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Catálogo simple de ministerios ACTIVOS (org-wide) para el select "Asignar a ministerio" del form de custodia. */
@Getter
@Setter
@AllArgsConstructor
public class InventoryMinistryOptionResponse {

    private UUID id;
    private String name;
}
