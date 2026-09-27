package pe.dcs.app.shared.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pe.dcs.app.util.Exceptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Almacenamiento en disco para desarrollo. La carpeta ({@code app.storage.local-dir}) no se versiona. */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private final Path root;

    public LocalFileStorageService(@Value("${app.storage.local-dir:./storage}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        } catch (IOException e) {
            log.error("No se pudo guardar {}", key, e);
            throw new Exceptions("error.common.storage", HttpStatus.BAD_GATEWAY);
        }
    }

    @Override
    public Optional<StoredFile> get(String key) {
        Path target = resolve(key);
        try {
            if (!Files.isRegularFile(target)) {
                return Optional.empty();
            }
            return Optional.of(new StoredFile(Files.readAllBytes(target), FileStorageService.contentTypeOf(key)));
        } catch (IOException e) {
            log.error("No se pudo leer {}", key, e);
            throw new Exceptions("error.common.storage", HttpStatus.BAD_GATEWAY);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("No se pudo borrar {}", key, e);
        }
    }

    /** Impide salir de la carpeta raíz con claves del tipo {@code ../../x}. */
    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "key");
        }
        return p;
    }
}
