package pe.dcs.app.features.doctemplate.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M18 · Motor de renderizado de PDF, compartido por toda plantilla ({@code document_template}/{@code template_base}).
 * El diseño (JSON) es una lista de elementos posicionados en mm desde la esquina superior izquierda de una página
 * A4 (210×297mm) o Carta (216×279mm); OpenPDF usa puntos (1mm = 2.834645pt) con origen inferior-izquierdo, de ahí el
 * volteo de Y. [D2] esto reemplaza a un lienzo de arrastrar-y-soltar (no existe librería de canvas en el stack).
 */
@Component
public class DocumentRenderService {

    private static final double MM_TO_PT = 2.834645;
    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*}}");

    public record RenderResult(byte[] pdf, String sha256) {
    }

    /** Renderiza el diseño con las variables dadas (ya resueltas por el llamador, incluidas number/date/qr). */
    public RenderResult render(Map<String, Object> design, Map<String, String> variables, byte[] qrPngOrNull, String qrPayloadOrNull) {
        String pageSize = str(design.get("pageSize"), "A4");
        String orientation = str(design.get("orientation"), "landscape");
        double[] wh = pageSize.equalsIgnoreCase("LETTER") ? new double[]{215.9, 279.4} : new double[]{210, 297};
        double widthMm = orientation.equalsIgnoreCase("landscape") ? Math.max(wh[0], wh[1]) : Math.min(wh[0], wh[1]);
        double heightMm = orientation.equalsIgnoreCase("landscape") ? Math.min(wh[0], wh[1]) : Math.max(wh[0], wh[1]);
        float pageW = (float) (widthMm * MM_TO_PT);
        float pageH = (float) (heightMm * MM_TO_PT);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document doc = new Document(new Rectangle(pageW, pageH), 0, 0, 0, 0);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();
            PdfContentByte cb = writer.getDirectContent();

            Object elementsObj = design.get("elements");
            if (elementsObj instanceof List<?> elements) {
                for (Object eo : elements) {
                    if (eo instanceof Map<?, ?> raw) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> el = (Map<String, Object>) raw;
                        drawElement(cb, el, variables, pageH, qrPngOrNull, qrPayloadOrNull);
                    }
                }
            }
            doc.close();
        } catch (Exception e) {
            throw new IllegalStateException("error.template.renderFailed", e);
        }
        byte[] pdf = out.toByteArray();
        return new RenderResult(pdf, sha256(pdf));
    }

    private void drawElement(PdfContentByte cb, Map<String, Object> el, Map<String, String> vars, float pageHPt, byte[] qrPng, String qrPayload) throws Exception {
        String type = str(el.get("type"), "TEXT").toUpperCase();
        double xMm = num(el.get("x"));
        double yMm = num(el.get("y"));
        double wMm = num(el.get("width"));
        double hMm = num(el.get("height"));
        float x = (float) (xMm * MM_TO_PT);
        float yTop = (float) (yMm * MM_TO_PT);
        float w = (float) (wMm * MM_TO_PT);
        float h = (float) (hMm * MM_TO_PT);
        float yPdf = pageHPt - yTop - h;                                                                                  // volteo: origen PDF es inferior-izquierda

        switch (type) {
            case "TEXT" -> {
                String content = resolve(str(el.get("content"), ""), vars);
                float size = (float) num(el.getOrDefault("fontSize", 12.0));
                String align = str(el.get("align"), "left").toLowerCase();
                boolean bold = Boolean.TRUE.equals(el.get("bold"));
                Font font = FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, size, Color.BLACK);
                int alignment = "center".equals(align) ? Element.ALIGN_CENTER : "right".equals(align) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT;
                float textY = pageHPt - yTop - size;
                ColumnText.showTextAligned(cb, alignment, new com.lowagie.text.Phrase(content, font),
                        alignment == Element.ALIGN_LEFT ? x : alignment == Element.ALIGN_RIGHT ? x + w : x + w / 2, textY, 0);
            }
            case "IMAGE", "SIGNATURE" -> {
                byte[] data = (byte[]) el.get("_bytes");                                                                  // el llamador resuelve la clave a bytes antes de invocar render()
                if (data != null && data.length > 0) {
                    Image img = Image.getInstance(data);
                    img.scaleToFit(w, h);
                    img.setAbsolutePosition(x, yPdf);
                    cb.addImage(img);
                }
            }
            case "QR" -> {
                if (qrPng != null) {
                    Image img = Image.getInstance(qrPng);
                    img.scaleToFit(w, h);
                    img.setAbsolutePosition(x, yPdf);
                    cb.addImage(img);
                }
            }
            default -> { /* tipo desconocido: se ignora, no rompe el render */ }
        }
    }

    /** Genera el PNG del QR (el payload típico es la URL pública de verificación) para reusar en varios renders (p. ej. reimpresión). */
    public byte[] qrPng(String payload, int sizePx) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx);
            BufferedImage img = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("error.template.qrFailed", e);
        }
    }

    public String resolve(String content, Map<String, String> vars) {
        if (content == null) {
            return "";
        }
        Matcher m = TOKEN.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = vars.getOrDefault(m.group(1), "");
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? "" : v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private static double num(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
