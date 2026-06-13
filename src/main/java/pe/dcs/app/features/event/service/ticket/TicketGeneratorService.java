package pe.dcs.app.features.event.service.ticket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.dcs.app.entity.Event;

import java.awt.image.BufferedImage;
import java.io.IOException;

@Service
@RequiredArgsConstructor
public class TicketGeneratorService {

    private final TicketTemplateService templateService;
    private final TicketPdfService pdfService;

    public byte[] generate(Event event, String token) {

        try {

            if (event.getTemplatePath() == null) {
                throw new RuntimeException("Template path is null");
            }

            if (token == null || token.isBlank()) {
                throw new RuntimeException("QR token is invalid");
            }
            BufferedImage image =
                    templateService.buildTicketImage(
                            event.getTemplatePath(),
                            token
                    );

            return pdfService.generatePdf(image);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(
                    "Error generating ticket PDF: " + e.getMessage(),
                    e
            );
        }
    }
}