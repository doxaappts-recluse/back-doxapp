package pe.dcs.app.features.event.service.ticket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.dcs.app.service.supabase.SupabaseStorageService;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class TicketTemplateService {

    private final SupabaseStorageService storageService;
    private final TicketQrService qrService;

    public BufferedImage buildTicketImage(String templatePath, String token) {

        try {

            InputStream templateStream =
                    storageService.download("events", templatePath);

            BufferedImage template =
                    ImageIO.read(templateStream);

            BufferedImage qr =
                    qrService.generateQr(token);

            int qrSize = 750;
            int marginX = 130;
            int marginY = 195;

            int x = template.getWidth() - qrSize - marginX;
            int y = template.getHeight() - qrSize - marginY;

            Graphics2D g = template.createGraphics();

            // opcional: mejora calidad del render
            g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            g.drawImage(qr, x, y, qrSize, qrSize, null);

            g.dispose();

            return template;

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error building ticket image", e);
        }
    }
}