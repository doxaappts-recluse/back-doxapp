package pe.dcs.app.features.event.service.ticket;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;

@Service
public class TicketQrService {

    public BufferedImage generateQr(String token) {

        try {

            QRCodeWriter writer = new QRCodeWriter();

            BitMatrix matrix = writer.encode(
                    token,
                    BarcodeFormat.QR_CODE,
                    300,
                    300
            );

            return MatrixToImageWriter.toBufferedImage(matrix);

        } catch (Exception e) {
            throw new RuntimeException("Error generating QR", e);
        }
    }
}