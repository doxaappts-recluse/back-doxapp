package pe.dcs.app.shared.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pe.dcs.app.service.supabase.SupabaseStorageService;
import pe.dcs.app.util.Exceptions;

import java.io.ByteArrayInputStream;
import java.util.Optional;

/** Supabase Storage (producción). Se activa con {@code app.storage.provider=supabase} + {@code SB_URL}/{@code SB_SERVICE_KEY}. */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "supabase")
public class SupabaseFileStorageService implements FileStorageService {

    private final SupabaseStorageService supabase;

    /** Clave del bucket en {@code supabase.storage.buckets}. */
    @Value("${app.storage.supabase-bucket:branding}")
    private String bucketKey;

    @Override
    public void put(String key, byte[] data, String contentType) {
        supabase.upload(new ByteArrayInputStream(data), bucketKey, key, contentType);
    }

    @Override
    public Optional<StoredFile> get(String key) {
        try {
            byte[] bytes = supabase.download(bucketKey, key).readAllBytes();
            return Optional.of(new StoredFile(bytes, FileStorageService.contentTypeOf(key)));
        } catch (Exceptions e) {
            log.debug("Archivo no disponible en Supabase: {}", key);
            return Optional.empty();
        } catch (java.io.IOException e) {
            throw new Exceptions("error.common.storage", HttpStatus.BAD_GATEWAY);
        }
    }

    @Override
    public void delete(String key) {
        try {
            supabase.delete(bucketKey, key);
        } catch (RuntimeException e) {
            log.warn("No se pudo borrar {} en Supabase: {}", key, e.getMessage());
        }
    }
}
