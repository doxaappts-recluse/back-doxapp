package pe.dcs.app.features.event.service.ticket;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import com.lowagie.text.Image;

@Service
public class TicketPdfService {

    public byte[] generatePdf(BufferedImage image) {

        try {

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);

            document.open();

            ByteArrayOutputStream imageOut = new ByteArrayOutputStream();

            ImageIO.write(image, "png", imageOut);

            Image pdfImage = Image.getInstance(imageOut.toByteArray());

            pdfImage.setAbsolutePosition(0, 0);
            pdfImage.scaleAbsolute(
                    PageSize.A4.getWidth(),
                    PageSize.A4.getHeight()
            );

            document.add(pdfImage);

            document.close();

            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Error generating PDF", e);
        }
    }
}