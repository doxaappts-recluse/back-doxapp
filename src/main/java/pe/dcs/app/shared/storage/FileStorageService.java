package pe.dcs.app.shared.storage;

import java.util.Optional;

/**
 * Núcleo 01 §2 · FileStorageService. Único punto de acceso a archivos: las claves siguen {@code org/{orgId}/...}
 * (multi-tenancy, spec 00 §7). Implementaciones: disco local (desarrollo, {@code app.storage.provider=local}, por
 * defecto) y Supabase Storage ({@code app.storage.provider=supabase}).
 */
public interface FileStorageService {

    /** Guarda (o reemplaza) el archivo. */
    void put(String key, byte[] data, String contentType);

    /** Contenido del archivo, o vacío si no existe. */
    Optional<StoredFile> get(String key);

    /** Borra el archivo; no falla si no existe. */
    void delete(String key);

    record StoredFile(byte[] data, String contentType) {
    }

    /** Tipo de contenido a partir de la extensión de la clave (los archivos de marca solo usan estas tres). */
    static String contentTypeOf(String key) {
        String k = key == null ? "" : key.toLowerCase();
        if (k.endsWith(".png")) {
            return "image/png";
        }
        if (k.endsWith(".webp")) {
            return "image/webp";
        }
        if (k.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }
}
