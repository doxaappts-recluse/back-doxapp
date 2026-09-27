package pe.dcs.app.features.person.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.dcs.app.features.person.dto.PersonDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.storage.FileStorageService;
import pe.dcs.app.util.Exceptions;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * M06 · Foto de la persona. Se acepta JPG o PNG (hasta 2 MB); se decodifica, se reduce a 512 px por lado y se guarda siempre
 * como JPG: así se descartan los metadatos (ubicación, cámara) y cualquier contenido que no sea una imagen real.
 * El archivo va por {@link FileStorageService} bajo {@code org/{orgId}/persons/{id}/photo.jpg}; solo lo ve quien ve a la persona.
 */
@Service
@RequiredArgsConstructor
public class PersonPhotoService {

    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_SIDE_IN = 6000;
    private static final int OUT_SIDE = 512;

    private final PersonLookupService lookup;
    private final NamedParameterJdbcTemplate jdbc;
    private final FileStorageService storage;
    private final AuditService audit;
    private final Clock clock;

    public record Photo(byte[] data, String contentType, long version) {
    }

    @Transactional(readOnly = true)
    public Photo get(AccessScope scope, UUID personId) {
        lookup.getVisible(scope, personId);
        Map<String, Object> row = jdbc.queryForMap("select photo_key, photo_updated_at from person where id = :id", new MapSqlParameterSource("id", personId));
        String key = (String) row.get("photo_key");
        if (key == null) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        FileStorageService.StoredFile f = storage.get(key).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        Timestamp at = (Timestamp) row.get("photo_updated_at");
        return new Photo(f.data(), "image/jpeg", at == null ? 0 : at.getTime());
    }

    @Transactional
    public PersonDtos.Photo put(AuthenticatedActor actor, AccessScope scope, UUID personId, MultipartFile file) {
        PersonLookupService.PersonMin p = lookup.getVisible(scope, personId);
        if (lookup.isAnonymized(personId)) {
            throw new Exceptions("error.person.anonymized", HttpStatus.CONFLICT);
        }
        if ("MERGED".equals(p.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, p.status());
        }
        if (file == null || file.isEmpty()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "file");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new Exceptions("error.common.fileSize", HttpStatus.BAD_REQUEST, "2 MB");
        }
        byte[] jpg;
        try {
            jpg = normalize(file.getBytes());
        } catch (IOException e) {
            throw new Exceptions("error.common.storage", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        String key = "org/" + scope.organizationId() + "/persons/" + personId + "/photo.jpg";
        storage.put(key, jpg, "image/jpeg");
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("update person set photo_key = :k, photo_updated_at = :at where id = :id", new MapSqlParameterSource("k", key).addValue("at", now).addValue("id", personId));
        audit.record(new AuditService.Command("PERSON", "PHOTO_SET", "Person", personId, scope.organizationId(), null, Map.of("bytes", jpg.length)));
        return new PersonDtos.Photo(true, now.toInstant());
    }

    @Transactional
    public PersonDtos.Photo delete(AuthenticatedActor actor, AccessScope scope, UUID personId) {
        lookup.getVisible(scope, personId);
        String key = jdbc.queryForObject("select photo_key from person where id = :id", new MapSqlParameterSource("id", personId), String.class);
        if (key != null) {
            storage.delete(key);
            jdbc.update("update person set photo_key = null, photo_updated_at = null where id = :id", new MapSqlParameterSource("id", personId));
            audit.record(new AuditService.Command("PERSON", "PHOTO_REMOVE", "Person", personId, scope.organizationId(), null, Map.of()));
        }
        return new PersonDtos.Photo(false, null);
    }

    /** Valida que sea JPG o PNG reales y devuelve un JPG cuadrado o proporcional de hasta 512 px por lado. */
    static byte[] normalize(byte[] raw) {
        if (!isJpeg(raw) && !isPng(raw)) {
            throw new Exceptions("error.common.fileType", HttpStatus.BAD_REQUEST, "JPG, PNG");
        }
        BufferedImage src;
        try {
            src = ImageIO.read(new ByteArrayInputStream(raw));
        } catch (IOException | RuntimeException e) {
            throw new Exceptions("error.person.photoInvalid", HttpStatus.BAD_REQUEST);
        }
        if (src == null || src.getWidth() < 1 || src.getHeight() < 1 || src.getWidth() > MAX_SIDE_IN || src.getHeight() > MAX_SIDE_IN) {
            throw new Exceptions("error.person.photoInvalid", HttpStatus.BAD_REQUEST);
        }
        double k = Math.min(1.0, OUT_SIDE / (double) Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * k));
        int h = Math.max(1, (int) Math.round(src.getHeight() * k));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(out, null, null), param);
            } finally {
                writer.dispose();
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new Exceptions("error.person.photoInvalid", HttpStatus.BAD_REQUEST);
        }
    }

    private static boolean isJpeg(byte[] d) {
        return d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] d) {
        return d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
    }
}
