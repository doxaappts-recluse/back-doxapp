package pe.dcs.app.features.access.dto;

import pe.dcs.app.shared.vo.DocumentType;

import java.util.UUID;

/** Persona de la organización que se puede elegir al dar un acceso (datos mínimos). */
public record PersonCandidate(UUID id, String fullName, DocumentType docType, String docNumber, String email, boolean hasCredential, long accesses) {
}
