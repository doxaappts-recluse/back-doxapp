package pe.dcs.app.features.support.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import pe.dcs.app.util.Exceptions;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** M22 V10 · adjuntos: hasta 5 por mensaje, 10 MB cada uno, solo pdf, png, jpg y txt (se revisa la firma real, no solo la extensión). */
@Component
public class SupportAttachmentPolicy {

    public static final int MAX_FILES = 5;
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    /** Adjunto ya validado, listo para guardar. */
    public record Checked(MultipartFile file, String safeName, String contentType) {
    }

    /** Ignora las partes vacías (un formulario sin archivo envía una) y valida el resto; nada se guarda si algo falla. */
    public List<Checked> check(List<MultipartFile> files) {
        List<MultipartFile> real = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (real.size() > MAX_FILES) {
            throw new Exceptions("error.support.tooManyAttachments", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return real.stream().map(this::checkOne).toList();
    }

    private Checked checkOne(MultipartFile f) {
        String original = f.getOriginalFilename() == null ? "" : f.getOriginalFilename();
        String base = original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).trim();
        int dot = base.lastIndexOf('.');
        String ext = dot < 0 ? "" : base.substring(dot + 1).toLowerCase(Locale.ROOT);
        String type = switch (ext) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "txt" -> "text/plain";
            default -> null;
        };
        if (type == null || f.getSize() > MAX_BYTES || !signatureOk(f, type)) {
            throw new Exceptions("error.support.attachmentInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String safe = base.replaceAll("[^A-Za-z0-9._ -]", "_");
        if (safe.length() > 120) {
            safe = safe.substring(safe.length() - 120);
        }
        return new Checked(f, safe, type);
    }

    private boolean signatureOk(MultipartFile f, String type) {
        try {
            byte[] b = f.getBytes();
            return switch (type) {
                case "application/pdf" -> b.length > 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-';
                case "image/png" -> b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
                case "image/jpeg" -> b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
                default -> noNul(b);
            };
        } catch (IOException e) {
            return false;
        }
    }

    private boolean noNul(byte[] b) {
        int n = Math.min(b.length, 4096);
        for (int i = 0; i < n; i++) {
            if (b[i] == 0) {
                return false;
            }
        }
        return true;
    }
}
