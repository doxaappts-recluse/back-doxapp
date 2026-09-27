package pe.dcs.app.shared.imports;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Lector mínimo de tablas para importaciones: CSV (coma, punto y coma o tabulación; UTF-8 con o sin BOM, o Windows-1252) y la
 * primera hoja de un XLSX. Sin librerías externas. Devuelve cada fila como lista de textos (celdas vacías = ""), sin recortar la
 * cabecera: quien llama decide qué columnas necesita. Protegido contra XML con entidades externas y contra ZIP inflados.
 */
public final class TabularReader {

    /** El archivo no se puede leer como CSV ni como XLSX, o supera los límites. */
    public static final class Unreadable extends RuntimeException {
        private final boolean tooLarge;

        Unreadable(String message, boolean tooLarge) {
            super(message);
            this.tooLarge = tooLarge;
        }

        public boolean tooLarge() {
            return tooLarge;
        }
    }

    private static final long MAX_ENTRY_BYTES = 40L * 1024 * 1024;
    private static final int MAX_ENTRIES = 200;
    private static final int MAX_COLS = 200;

    private TabularReader() {
    }

    /** Lee hasta {@code maxRows} filas (incluida la cabecera); si el archivo tiene más lanza {@link Unreadable} con tooLarge. */
    public static List<List<String>> read(String fileName, byte[] data, int maxRows) {
        boolean zip = data.length > 3 && data[0] == 'P' && data[1] == 'K';
        String name = fileName == null ? "" : fileName.toLowerCase();
        if (zip) {
            return readXlsx(data, maxRows);
        }
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            throw new Unreadable("xlsx", false);
        }
        return readCsv(data, maxRows);
    }

    // ---------------------------------------------------------------- CSV

    static List<List<String>> readCsv(byte[] data, int maxRows) {
        String text = decode(data);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        char delim = delimiter(text);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(c);
                }
                continue;
            }
            if (c == '"' && cell.length() == 0) {
                quoted = true;
            } else if (c == delim) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                addRow(rows, row, maxRows);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
            if (row.size() > MAX_COLS) {
                throw new Unreadable("columns", false);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            addRow(rows, row, maxRows);
        }
        return rows;
    }

    private static void addRow(List<List<String>> rows, List<String> row, int maxRows) {
        if (row.stream().allMatch(String::isBlank)) {
            return;                                                    // las líneas vacías no cuentan
        }
        if (rows.size() >= maxRows) {
            throw new Unreadable("rows", true);
        }
        rows.add(row);
    }

    private static String decode(byte[] data) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            return new String(data, Charset.forName("windows-1252"));
        }
    }

    private static char delimiter(String text) {
        int end = text.indexOf('\n');
        String first = end < 0 ? text : text.substring(0, end);
        int comma = 0, semi = 0, tab = 0;
        boolean q = false;
        for (char c : first.toCharArray()) {
            if (c == '"') {
                q = !q;
            } else if (!q) {
                if (c == ',') {
                    comma++;
                } else if (c == ';') {
                    semi++;
                } else if (c == '\t') {
                    tab++;
                }
            }
        }
        if (semi > comma && semi >= tab) {
            return ';';
        }
        return tab > comma ? '\t' : ',';
    }

    // ---------------------------------------------------------------- XLSX

    static List<List<String>> readXlsx(byte[] data, int maxRows) {
        Map<String, byte[]> parts = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            int entries = 0;
            while ((e = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new Unreadable("zip", false);
                }
                String name = e.getName();
                if (name.equals("xl/sharedStrings.xml") || name.equals("xl/workbook.xml") || name.equals("xl/_rels/workbook.xml.rels")
                        || (name.startsWith("xl/worksheets/") && name.endsWith(".xml"))) {
                    parts.put(name, readLimited(zip));
                }
            }
        } catch (IOException e) {
            throw new Unreadable("zip", false);
        }
        String sheet = firstSheet(parts);
        byte[] xml = parts.get(sheet);
        if (xml == null) {
            throw new Unreadable("sheet", false);
        }
        List<String> shared = parts.containsKey("xl/sharedStrings.xml") ? sharedStrings(parts.get("xl/sharedStrings.xml")) : List.of();
        return sheetRows(xml, shared, maxRows);
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        long total = 0;
        int r;
        while ((r = in.read(buf)) > 0) {
            total += r;
            if (total > MAX_ENTRY_BYTES) {
                throw new Unreadable("inflated", true);
            }
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }

    private static XMLInputFactory factory() {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return f;
    }

    /** Ruta de la primera hoja del libro (según workbook.xml); por defecto la más baja de xl/worksheets. */
    private static String firstSheet(Map<String, byte[]> parts) {
        try {
            byte[] wb = parts.get("xl/workbook.xml");
            byte[] rels = parts.get("xl/_rels/workbook.xml.rels");
            if (wb != null && rels != null) {
                String rid = null;
                XMLStreamReader r = factory().createXMLStreamReader(new ByteArrayInputStream(wb));
                while (r.hasNext() && rid == null) {
                    if (r.next() == XMLStreamConstants.START_ELEMENT && r.getLocalName().equals("sheet")) {
                        for (int i = 0; i < r.getAttributeCount(); i++) {
                            if (r.getAttributeLocalName(i).equals("id")) {
                                rid = r.getAttributeValue(i);
                            }
                        }
                    }
                }
                if (rid != null) {
                    XMLStreamReader rr = factory().createXMLStreamReader(new ByteArrayInputStream(rels));
                    while (rr.hasNext()) {
                        if (rr.next() == XMLStreamConstants.START_ELEMENT && rr.getLocalName().equals("Relationship") && rid.equals(rr.getAttributeValue(null, "Id"))) {
                            String t = rr.getAttributeValue(null, "Target");
                            if (t != null) {
                                return t.startsWith("/") ? t.substring(1) : "xl/" + t;
                            }
                        }
                    }
                }
            }
        } catch (XMLStreamException ignored) {
            // se usa la hoja por defecto
        }
        return parts.keySet().stream().filter(k -> k.startsWith("xl/worksheets/")).sorted().findFirst().orElse("xl/worksheets/sheet1.xml");
    }

    private static List<String> sharedStrings(byte[] xml) {
        List<String> out = new ArrayList<>();
        try {
            XMLStreamReader r = factory().createXMLStreamReader(new ByteArrayInputStream(xml));
            StringBuilder cur = null;
            boolean inT = false;
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    String n = r.getLocalName();
                    if (n.equals("si")) {
                        cur = new StringBuilder();
                    } else if (n.equals("t") && cur != null) {
                        inT = true;
                    }
                } else if (ev == XMLStreamConstants.CHARACTERS && inT && cur != null) {
                    cur.append(r.getText());
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    String n = r.getLocalName();
                    if (n.equals("t")) {
                        inT = false;
                    } else if (n.equals("si") && cur != null) {
                        out.add(cur.toString());
                        cur = null;
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new Unreadable("xml", false);
        }
        return out;
    }

    private static List<List<String>> sheetRows(byte[] xml, List<String> shared, int maxRows) {
        List<List<String>> rows = new ArrayList<>();
        try {
            XMLStreamReader r = factory().createXMLStreamReader(new ByteArrayInputStream(xml));
            List<String> row = null;
            int col = 0;
            String type = null;
            StringBuilder text = null;
            boolean inV = false, inT = false;
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "row" -> row = new ArrayList<>();
                        case "c" -> {
                            type = r.getAttributeValue(null, "t");
                            col = columnIndex(r.getAttributeValue(null, "r"), row == null ? 0 : row.size());
                            if (col >= MAX_COLS) {
                                throw new Unreadable("columns", false);
                            }
                            text = new StringBuilder();
                        }
                        case "v" -> inV = true;
                        case "t" -> inT = true;
                        default -> {
                        }
                    }
                } else if (ev == XMLStreamConstants.CHARACTERS) {
                    if ((inV || inT) && text != null) {
                        text.append(r.getText());
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "v" -> inV = false;
                        case "t" -> inT = false;
                        case "c" -> {
                            if (row != null && text != null) {
                                while (row.size() < col) {
                                    row.add("");
                                }
                                row.add(cellValue(type, text.toString(), shared));
                            }
                            text = null;
                        }
                        case "row" -> {
                            if (row != null && !row.stream().allMatch(String::isBlank)) {
                                if (rows.size() >= maxRows) {
                                    throw new Unreadable("rows", true);
                                }
                                rows.add(row);
                            }
                            row = null;
                        }
                        default -> {
                        }
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new Unreadable("xml", false);
        }
        return rows;
    }

    private static String cellValue(String type, String raw, List<String> shared) {
        if ("s".equals(type)) {
            try {
                int i = Integer.parseInt(raw.trim());
                return i >= 0 && i < shared.size() ? shared.get(i) : "";
            } catch (NumberFormatException e) {
                return "";
            }
        }
        if ("inlineStr".equals(type) || "str".equals(type) || "e".equals(type)) {
            return raw;
        }
        if ("b".equals(type)) {
            return "1".equals(raw.trim()) ? "1" : "0";
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return "";
        }
        try {
            BigDecimal d = new BigDecimal(v);
            return d.stripTrailingZeros().toPlainString();                                  // 40000005.0 → 40000005
        } catch (NumberFormatException e) {
            return v;
        }
    }

    /** "C5" → 2. Si la celda no trae referencia se usa la siguiente posición. */
    private static int columnIndex(String ref, int fallback) {
        if (ref == null || ref.isEmpty()) {
            return fallback;
        }
        int c = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                c = c * 26 + (ch - 'A' + 1);
            } else if (ch >= 'a' && ch <= 'z') {
                c = c * 26 + (ch - 'a' + 1);
            } else {
                break;
            }
        }
        return Math.max(0, c - 1);
    }
}
