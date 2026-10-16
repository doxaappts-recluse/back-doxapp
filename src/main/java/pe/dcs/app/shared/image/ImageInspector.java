package pe.dcs.app.shared.image;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inspección de imágenes de marca (M02 [V11]): el tipo se decide por el CONTENIDO (no por la extensión ni el
 * Content-Type declarado), se comprueban las dimensiones y los SVG se sanean (sin scripts, eventos ni referencias
 * externas). Solo png, webp y svg.
 */
public final class ImageInspector {

    public enum Kind {
        PNG("png", "image/png"), WEBP("webp", "image/webp"), SVG("svg", "image/svg+xml");

        public final String extension;
        public final String contentType;

        Kind(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }
    }

    /** Resultado: el tipo real y los bytes a guardar (los del SVG, ya saneados). */
    public record Inspected(Kind kind, byte[] data) {
    }

    /** Se lanza cuando el archivo no cumple; el llamador lo traduce a {@code error.org.logoInvalid}. */
    public static class InvalidImageException extends RuntimeException {
        public InvalidImageException(String message) {
            super(message);
        }
    }

    public static final int MAX_BYTES = 512 * 1024;
    public static final int MAX_SIDE = 1024;

    private static final Set<String> DANGEROUS_ELEMENTS = Set.of(
            "script", "foreignobject", "iframe", "object", "embed", "audio", "video", "canvas", "link", "meta",
            "animate", "set", "animatemotion", "animatetransform", "handler", "listener");
    private static final Pattern CSS_DANGEROUS = Pattern.compile("(?i)@import|expression\\s*\\(|javascript:|url\\s*\\(\\s*['\"]?\\s*(?!#)");
    private static final Pattern DATA_IMAGE = Pattern.compile("(?i)^data:image/(png|jpeg|jpg|webp|gif);base64,[a-z0-9+/=\\s]+$");
    private static final Pattern NUMBER = Pattern.compile("^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(px)?\\s*$");

    private ImageInspector() {
    }

    public static Inspected inspect(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_BYTES) {
            throw new InvalidImageException("size");
        }
        if (isPng(data)) {
            int w = be32(data, 16);
            int h = be32(data, 20);
            checkSide(w, h);
            return new Inspected(Kind.PNG, data);
        }
        if (isWebp(data)) {
            int[] wh = webpSize(data);
            checkSide(wh[0], wh[1]);
            return new Inspected(Kind.WEBP, data);
        }
        if (looksLikeSvg(data)) {
            return new Inspected(Kind.SVG, sanitizeSvg(data));
        }
        throw new InvalidImageException("type");
    }

    // ------------------------------------------------------------------ PNG / WEBP

    private static boolean isPng(byte[] d) {
        return d.length > 24 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A;
    }

    private static boolean isWebp(byte[] d) {
        return d.length > 30 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
    }

    private static int be32(byte[] d, int o) {
        return ((d[o] & 0xFF) << 24) | ((d[o + 1] & 0xFF) << 16) | ((d[o + 2] & 0xFF) << 8) | (d[o + 3] & 0xFF);
    }

    private static int le24(byte[] d, int o) {
        return (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8) | ((d[o + 2] & 0xFF) << 16);
    }

    private static int[] webpSize(byte[] d) {
        String chunk = new String(d, 12, 4, StandardCharsets.US_ASCII);
        switch (chunk) {
            case "VP8X":
                return new int[]{1 + le24(d, 24), 1 + le24(d, 27)};
            case "VP8 ":
                if (d.length < 30) {
                    break;
                }
                return new int[]{(d[26] & 0xFF | (d[27] & 0x3F) << 8), (d[28] & 0xFF | (d[29] & 0x3F) << 8)};
            case "VP8L":
                if (d.length < 25 || (d[20] & 0xFF) != 0x2F) {
                    break;
                }
                int b0 = d[21] & 0xFF, b1 = d[22] & 0xFF, b2 = d[23] & 0xFF, b3 = d[24] & 0xFF;
                int w = 1 + (((b1 & 0x3F) << 8) | b0);
                int h = 1 + (((b3 & 0x0F) << 10) | (b2 << 2) | ((b1 & 0xC0) >> 6));
                return new int[]{w, h};
            default:
                break;
        }
        throw new InvalidImageException("webp");
    }

    private static void checkSide(int w, int h) {
        if (w <= 0 || h <= 0 || w > MAX_SIDE || h > MAX_SIDE) {
            throw new InvalidImageException("dimensions");
        }
    }

    // ------------------------------------------------------------------ SVG

    private static boolean looksLikeSvg(byte[] d) {
        String head = new String(d, 0, Math.min(d.length, 1024), StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
        if (head.startsWith("﻿")) {
            head = head.substring(1).trim();
        }
        return head.startsWith("<svg") || (head.startsWith("<?xml") && head.contains("<svg"));
    }

    /** Parsea sin DTD/entidades externas, quita lo peligroso y vuelve a serializar. Lo que no se puede sanear se rechaza. */
    static byte[] sanitizeSvg(byte[] data) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            Document doc = f.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(data)));
            Element root = doc.getDocumentElement();
            if (!"svg".equalsIgnoreCase(root.getLocalName() == null ? root.getNodeName() : root.getLocalName())) {
                throw new InvalidImageException("svg");
            }
            clean(root);
            checkSvgSize(root);

            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            t.transform(new DOMSource(doc), new StreamResult(out));
            byte[] clean = out.toByteArray();
            if (clean.length > MAX_BYTES) {
                throw new InvalidImageException("size");
            }
            return clean;
        } catch (InvalidImageException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidImageException("svg");
        }
    }

    private static void clean(Element el) {
        // atributos
        NamedNodeMap attrs = el.getAttributes();
        List<String> remove = new ArrayList<>();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr a = (Attr) attrs.item(i);
            String name = a.getName().toLowerCase(Locale.ROOT);
            String value = a.getValue() == null ? "" : a.getValue().trim();
            if (name.startsWith("on")) {
                remove.add(a.getName());
            } else if (name.equals("href") || name.endsWith(":href")) {
                if (!(value.startsWith("#") || DATA_IMAGE.matcher(value).matches())) {
                    remove.add(a.getName()); // solo referencias internas o imágenes incrustadas
                }
            } else if (name.equals("style") && CSS_DANGEROUS.matcher(value).find()) {
                remove.add(a.getName());
            } else if (value.toLowerCase(Locale.ROOT).contains("javascript:")) {
                remove.add(a.getName());
            }
        }
        remove.forEach(el::removeAttribute);

        // hijos
        NodeList children = el.getChildNodes();
        List<Node> toRemove = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                Element c = (Element) n;
                String local = (c.getLocalName() == null ? c.getNodeName() : c.getLocalName()).toLowerCase(Locale.ROOT);
                if (DANGEROUS_ELEMENTS.contains(local)) {
                    toRemove.add(c);
                } else if (local.equals("style") && CSS_DANGEROUS.matcher(c.getTextContent()).find()) {
                    toRemove.add(c);
                } else {
                    clean(c);
                }
            } else if (n.getNodeType() == Node.COMMENT_NODE || n.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE
                    || n.getNodeType() == Node.ENTITY_REFERENCE_NODE) {
                toRemove.add(n);
            }
        }
        toRemove.forEach(el::removeChild);
    }

    private static void checkSvgSize(Element root) {
        Double w = length(root.getAttribute("width"));
        Double h = length(root.getAttribute("height"));
        if ((w != null && w > MAX_SIDE) || (h != null && h > MAX_SIDE)) {
            throw new InvalidImageException("dimensions");
        }
    }

    private static Double length(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        Matcher m = NUMBER.matcher(v);
        return m.matches() ? Double.parseDouble(m.group(1)) : null; // %, em… no se pueden comparar: se aceptan
    }
}
