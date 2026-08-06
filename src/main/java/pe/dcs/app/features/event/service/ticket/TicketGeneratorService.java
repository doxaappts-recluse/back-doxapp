package pe.dcs.app.features.event.service.ticket;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pe.dcs.app.entity.Event;
import pe.dcs.app.util.Exceptions;

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
                throw new Exceptions(
                        "error.plantillaTicketNoConfigurada",
                        HttpStatus.BAD_REQUEST
                );
            }

            if (token == null || token.isBlank()) {
                throw new Exceptions(
                        "error.tokenQrInvalido",
                        HttpStatus.BAD_REQUEST
                );
            }
            BufferedImage image =
                    templateService.buildTicketImage(
                            event.getTemplatePath(),
                            token
                    );

            return pdfService.generatePdf(image);

        } catch (Exceptions e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exceptions(
                    "error.errorGenerandoTicketPdf",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}