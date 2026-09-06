package com.toddle.bagweigher.xlsx;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A tiny, dependency-free .xlsx (SpreadsheetML) writer.
 *
 * Only what this app needs: numbers, inline strings, formulas with cached
 * values, per-cell fonts / borders / alignment / number formats, merged
 * ranges, column widths, row heights and page setup.
 *
 * It uses nothing outside java.util.zip + java.lang, so the exact same file
 * compiles on Android and on a desktop JVM.
 */
public final class XlsxWriter {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    /* ------------------------------------------------------------------ */
    /* Styles                                                              */
    /* ------------------------------------------------------------------ */

    /** Immutable-ish cell style. Use the with*() helpers to derive variants. */
    public static final class Style {
        public boolean bold;
        public double fontSize = 11;
        public String fontName = "Calibri";
        /** true -> "0.00", false -> General */
        public boolean twoDecimals;
        /** null | "left" | "center" | "right" */
        public String hAlign;
        /** null | "thin" | "medium" */
        public String left, right, top, bottom;
        /** solid background as "RRGGBB", or null */
        public String fill;
        /** font colour as "RRGGBB", or null for the default */
        public String color;

        public Style copy() {
            Style s = new Style();
            s.bold = bold;
            s.fontSize = fontSize;
            s.fontName = fontName;
            s.twoDecimals = twoDecimals;
            s.hAlign = hAlign;
            s.left = left;
            s.right = right;
            s.top = top;
            s.bottom = bottom;
            s.fill = fill;
            s.color = color;
            return s;
        }

        public Style bold(boolean b) { Style s = copy(); s.bold = b; return s; }
        public Style font(String name, double size) { Style s = copy(); s.fontName = name; s.fontSize = size; return s; }
        public Style decimals(boolean b) { Style s = copy(); s.twoDecimals = b; return s; }
        public Style align(String a) { Style s = copy(); s.hAlign = a; return s; }
        public Style borders(String l, String r, String t, String b) {
            Style s = copy(); s.left = l; s.right = r; s.top = t; s.bottom = b; return s;
        }
        public Style withLeft(String v) { Style s = copy(); s.left = v; return s; }
        public Style withRight(String v) { Style s = copy(); s.right = v; return s; }
        public Style withTop(String v) { Style s = copy(); s.top = v; return s; }
        public Style withBottom(String v) { Style s = copy(); s.bottom = v; return s; }
        public Style fill(String rgb) { Style s = copy(); s.fill = rgb; return s; }
        public Style color(String rgb) { Style s = copy(); s.color = rgb; return s; }

        String key() {
            return bold + "|" + fontSize + "|" + fontName + "|" + twoDecimals + "|" + hAlign
                    + "|" + left + "|" + right + "|" + top + "|" + bottom + "|" + fill + "|" + color;
        }
    }

    public static Style style() { return new Style(); }

    /* ------------------------------------------------------------------ */
    /* Cells / sheets                                                      */
    /* ------------------------------------------------------------------ */

    private static final class Cell {
        String text;      // inline string, or null
        Double number;    // numeric value / cached formula result, or null
        String formula;   // formula without the leading '=', or null
        Style style;
    }

    public static final class Sheet {
        final String name;
        // row -> (col -> cell); 1-based indices
        final TreeMap<Integer, TreeMap<Integer, Cell>> rows = new TreeMap<Integer, TreeMap<Integer, Cell>>();
        final TreeMap<Integer, Double> rowHeights = new TreeMap<Integer, Double>();
        final TreeMap<Integer, Double> colWidths = new TreeMap<Integer, Double>();
        final List<String> merges = new ArrayList<String>();
        boolean landscape = true;
        int paperSize = 9; // A4
        boolean showGridLines = true;
        int fitToWidth = 0;          // 0 = off, 1 = squeeze onto one page wide

        // optional picture, anchored at one cell and drawn at its own size
        byte[] imageBytes;
        String imageExt = "png";
        int imgRow = 1, imgCol = 1, imgWidthPx, imgHeightPx;

        Sheet(String name) { this.name = name; }

        private Cell cell(int row, int col, Style style) {
            TreeMap<Integer, Cell> r = rows.get(row);
            if (r == null) { r = new TreeMap<Integer, Cell>(); rows.put(row, r); }
            Cell c = r.get(col);
            if (c == null) { c = new Cell(); r.put(col, c); }
            c.style = style;
            return c;
        }

        public void str(int row, int col, String value, Style style) {
            cell(row, col, style).text = value;
        }

        public void num(int row, int col, double value, Style style) {
            cell(row, col, style).number = Double.valueOf(value);
        }

        /** formula without '=', e.g. "SUM(B4:B13)"; cached may be null. */
        public void formula(int row, int col, String formula, Double cached, Style style) {
            Cell c = cell(row, col, style);
            c.formula = formula;
            c.number = cached;
        }

        /** paints a style on an otherwise empty cell (keeps the grid lines going) */
        public void blank(int row, int col, Style style) {
            cell(row, col, style);
        }

        public void rowHeight(int row, double height) { rowHeights.put(row, height); }
        public void colWidth(int col, double width) { colWidths.put(col, width); }
        public void merge(int r1, int c1, int r2, int c2) {
            merges.add(ref(r1, c1) + ":" + ref(r2, c2));
        }

        public void gridLines(boolean on) { showGridLines = on; }

        public void fitToOnePageWide() { fitToWidth = 1; }

        /**
         * Anchors a picture at one cell and draws it at the given pixel size, so
         * the aspect ratio is whatever the caller asks for — never stretched to
         * fit a cell range.
         *
         * @param ext "png" or "jpeg"
         */
        public void image(byte[] bytes, String ext, int row, int col, int widthPx, int heightPx) {
            if (bytes == null || bytes.length == 0) return;
            imageBytes = bytes;
            imageExt = ext == null ? "png" : ext;
            imgRow = row;
            imgCol = col;
            imgWidthPx = widthPx;
            imgHeightPx = heightPx;
        }
    }

    private final List<Sheet> sheets = new ArrayList<Sheet>();

    public Sheet addSheet(String name) {
        Sheet s = new Sheet(name);
        sheets.add(s);
        return s;
    }

    /* ------------------------------------------------------------------ */
    /* Cell references                                                     */
    /* ------------------------------------------------------------------ */

    /** 1-based column index -> "A", "B", ... "AA" */
    public static String colName(int col) {
        StringBuilder sb = new StringBuilder();
        int c = col;
        while (c > 0) {
            int rem = (c - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            c = (c - 1) / 26;
        }
        return sb.toString();
    }

    public static String ref(int row, int col) { return colName(col) + row; }

    /* ------------------------------------------------------------------ */
    /* Writing                                                             */
    /* ------------------------------------------------------------------ */

    public void write(OutputStream out) throws IOException {
        // Collect styles first so every sheet refers to the same table.
        StyleTable styles = new StyleTable();
        for (int i = 0; i < sheets.size(); i++) {
            Sheet sh = sheets.get(i);
            for (Map.Entry<Integer, TreeMap<Integer, Cell>> re : sh.rows.entrySet()) {
                for (Map.Entry<Integer, Cell> ce : re.getValue().entrySet()) {
                    Cell c = ce.getValue();
                    if (c.style != null) styles.idFor(c.style);
                }
            }
        }

        ZipOutputStream zip = new ZipOutputStream(out);
        zip.setLevel(6);
        put(zip, "[Content_Types].xml", contentTypes());
        put(zip, "_rels/.rels", rootRels());
        put(zip, "xl/workbook.xml", workbookXml());
        put(zip, "xl/_rels/workbook.xml.rels", workbookRels());
        put(zip, "xl/styles.xml", styles.toXml());
        for (int i = 0; i < sheets.size(); i++) {
            Sheet sh = sheets.get(i);
            put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheetXml(sh, styles));
            if (sh.imageBytes != null) {
                int n = i + 1;
                put(zip, "xl/worksheets/_rels/sheet" + n + ".xml.rels",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rIdDrawing\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing\" Target=\"../drawings/drawing" + n + ".xml\"/>"
                        + "</Relationships>");
                put(zip, "xl/drawings/drawing" + n + ".xml", drawingXml(sh));
                put(zip, "xl/drawings/_rels/drawing" + n + ".xml.rels",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rIdImage\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/image" + n + "." + sh.imageExt + "\"/>"
                        + "</Relationships>");
                putBytes(zip, "xl/media/image" + n + "." + sh.imageExt, sh.imageBytes);
            }
        }
        zip.finish();
        zip.flush();
    }

    private static void put(ZipOutputStream zip, String path, String content) throws IOException {
        putBytes(zip, path, content.getBytes(UTF8));
    }

    private static void putBytes(ZipOutputStream zip, String path, byte[] content) throws IOException {
        ZipEntry e = new ZipEntry(path);
        zip.putNextEntry(e);
        zip.write(content);
        zip.closeEntry();
    }

    /** one picture, anchored over a cell range */
    private String drawingXml(Sheet sh) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" ")
          .append("xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" ")
          .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
        long cx = (long) sh.imgWidthPx * 9525L;    // 1 px = 9525 EMU
        long cy = (long) sh.imgHeightPx * 9525L;
        sb.append("<xdr:oneCellAnchor>");
        sb.append("<xdr:from><xdr:col>").append(sh.imgCol - 1).append("</xdr:col><xdr:colOff>38100</xdr:colOff>")
          .append("<xdr:row>").append(sh.imgRow - 1).append("</xdr:row><xdr:rowOff>28575</xdr:rowOff></xdr:from>");
        sb.append("<xdr:ext cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/>");
        sb.append("<xdr:pic>");
        sb.append("<xdr:nvPicPr><xdr:cNvPr id=\"1\" name=\"Logo\"/><xdr:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></xdr:cNvPicPr></xdr:nvPicPr>");
        sb.append("<xdr:blipFill><a:blip r:embed=\"rIdImage\"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>");
        sb.append("<xdr:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"").append(cx)
          .append("\" cy=\"").append(cy).append("\"/></a:xfrm>")
          .append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr>");
        sb.append("</xdr:pic><xdr:clientData/></xdr:oneCellAnchor></xdr:wsDr>");
        return sb.toString();
    }

    private String contentTypes() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
        sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
        boolean png = false, jpeg = false;
        for (int i = 0; i < sheets.size(); i++) {
            Sheet sh = sheets.get(i);
            if (sh.imageBytes == null) continue;
            if ("png".equals(sh.imageExt)) png = true; else jpeg = true;
        }
        if (png) sb.append("<Default Extension=\"png\" ContentType=\"image/png\"/>");
        if (jpeg) sb.append("<Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>");
        for (int i = 0; i < sheets.size(); i++) {
            if (sheets.get(i).imageBytes == null) continue;
            sb.append("<Override PartName=\"/xl/drawings/drawing").append(i + 1)
              .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>");
        }
        sb.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        sb.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1)
              .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        sb.append("</Types>");
        return sb.toString();
    }

    private String rootRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private String workbookXml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
          .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
        sb.append("<sheets>");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<sheet name=\"").append(esc(sheets.get(i).name)).append("\" sheetId=\"")
              .append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        sb.append("</sheets>");
        sb.append("<calcPr fullCalcOnLoad=\"1\"/>");
        sb.append("</workbook>");
        return sb.toString();
    }

    private String workbookRels() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<Relationship Id=\"rId").append(i + 1)
              .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
              .append(i + 1).append(".xml\"/>");
        }
        sb.append("<Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        sb.append("</Relationships>");
        return sb.toString();
    }

    private String sheetXml(Sheet sh, StyleTable styles) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
          .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");

        int maxRow = sh.rows.isEmpty() ? 1 : sh.rows.lastKey();
        int maxCol = 1;
        for (TreeMap<Integer, Cell> r : sh.rows.values()) {
            if (!r.isEmpty() && r.lastKey() > maxCol) maxCol = r.lastKey();
        }
        if (sh.fitToWidth > 0) sb.append("<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr>");
        sb.append("<dimension ref=\"A1:").append(ref(maxRow, maxCol)).append("\"/>");
        sb.append("<sheetViews><sheetView workbookViewId=\"0\"")
          .append(sh.showGridLines ? "" : " showGridLines=\"0\"").append("/></sheetViews>");
        sb.append("<sheetFormatPr defaultRowHeight=\"15\"/>");

        if (!sh.colWidths.isEmpty()) {
            sb.append("<cols>");
            for (Map.Entry<Integer, Double> e : sh.colWidths.entrySet()) {
                sb.append("<col min=\"").append(e.getKey()).append("\" max=\"").append(e.getKey())
                  .append("\" width=\"").append(num(e.getValue().doubleValue()))
                  .append("\" customWidth=\"1\"/>");
            }
            sb.append("</cols>");
        }

        sb.append("<sheetData>");
        for (Map.Entry<Integer, TreeMap<Integer, Cell>> re : sh.rows.entrySet()) {
            int rowIdx = re.getKey().intValue();
            sb.append("<row r=\"").append(rowIdx).append("\"");
            Double h = sh.rowHeights.get(re.getKey());
            if (h != null) sb.append(" ht=\"").append(num(h.doubleValue())).append("\" customHeight=\"1\"");
            sb.append(">");
            for (Map.Entry<Integer, Cell> ce : re.getValue().entrySet()) {
                int colIdx = ce.getKey().intValue();
                Cell c = ce.getValue();
                int sid = c.style == null ? 0 : styles.idFor(c.style);
                sb.append("<c r=\"").append(ref(rowIdx, colIdx)).append("\"");
                if (sid != 0) sb.append(" s=\"").append(sid).append("\"");
                if (c.formula != null) {
                    sb.append("><f>").append(esc(c.formula)).append("</f>");
                    if (c.number != null) sb.append("<v>").append(num(c.number.doubleValue())).append("</v>");
                    sb.append("</c>");
                } else if (c.number != null) {
                    sb.append("><v>").append(num(c.number.doubleValue())).append("</v></c>");
                } else if (c.text != null && c.text.length() > 0) {
                    sb.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                      .append(esc(c.text)).append("</t></is></c>");
                } else {
                    sb.append("/>");
                }
            }
            sb.append("</row>");
        }
        sb.append("</sheetData>");

        if (!sh.merges.isEmpty()) {
            sb.append("<mergeCells count=\"").append(sh.merges.size()).append("\">");
            for (int i = 0; i < sh.merges.size(); i++) {
                sb.append("<mergeCell ref=\"").append(sh.merges.get(i)).append("\"/>");
            }
            sb.append("</mergeCells>");
        }

        sb.append("<pageMargins left=\"0.52\" right=\"0.2\" top=\"0.47\" bottom=\"0.43\" header=\"0.31\" footer=\"0.31\"/>");
        sb.append("<pageSetup paperSize=\"").append(sh.paperSize).append("\" orientation=\"")
          .append(sh.landscape ? "landscape" : "portrait").append("\"")
          .append(sh.fitToWidth > 0 ? " fitToWidth=\"1\" fitToHeight=\"1\"" : "").append("/>");
        if (sh.imageBytes != null) sb.append("<drawing r:id=\"rIdDrawing\"/>");
        sb.append("</worksheet>");
        return sb.toString();
    }

    /* ------------------------------------------------------------------ */
    /* Style table                                                         */
    /* ------------------------------------------------------------------ */

    private static final class StyleTable {
        private final LinkedHashMap<String, Integer> fonts = new LinkedHashMap<String, Integer>();
        private final LinkedHashMap<String, Integer> borders = new LinkedHashMap<String, Integer>();
        private final LinkedHashMap<String, Integer> fills = new LinkedHashMap<String, Integer>();
        private final List<String> fillXml = new ArrayList<String>();
        private final LinkedHashMap<String, Integer> xfs = new LinkedHashMap<String, Integer>();
        private final List<String> fontXml = new ArrayList<String>();
        private final List<String> borderXml = new ArrayList<String>();
        private final List<int[]> xfDefs = new ArrayList<int[]>();     // {fontId, borderId, numFmtId, fillId}
        private final List<String> xfAlign = new ArrayList<String>();

        StyleTable() {
            // index 0 = default
            font(false, 11, "Calibri", null);
            border(null, null, null, null);
            // fills 0 and 1 are reserved by the format
            fillXml.add("<fill><patternFill patternType=\"none\"/></fill>");
            fillXml.add("<fill><patternFill patternType=\"gray125\"/></fill>");
            xfs.put("default", Integer.valueOf(0));
            xfDefs.add(new int[]{0, 0, 0, 0});
            xfAlign.add(null);
        }

        private int font(boolean bold, double size, String name, String color) {
            String key = bold + "|" + size + "|" + name + "|" + color;
            Integer id = fonts.get(key);
            if (id != null) return id.intValue();
            int newId = fontXml.size();
            fontXml.add("<font>" + (bold ? "<b/>" : "") + "<sz val=\"" + num(size) + "\"/>"
                    + (color == null ? "<color theme=\"1\"/>" : "<color rgb=\"FF" + color + "\"/>")
                    + "<name val=\"" + esc(name) + "\"/></font>");
            fonts.put(key, Integer.valueOf(newId));
            return newId;
        }

        private int border(String l, String r, String t, String b) {
            String key = l + "|" + r + "|" + t + "|" + b;
            Integer id = borders.get(key);
            if (id != null) return id.intValue();
            int newId = borderXml.size();
            StringBuilder sb = new StringBuilder("<border>");
            sb.append(side("left", l)).append(side("right", r))
              .append(side("top", t)).append(side("bottom", b)).append("<diagonal/>");
            sb.append("</border>");
            borderXml.add(sb.toString());
            borders.put(key, Integer.valueOf(newId));
            return newId;
        }

        private static String side(String tag, String style) {
            if (style == null) return "<" + tag + "/>";
            return "<" + tag + " style=\"" + style + "\"><color indexed=\"64\"/></" + tag + ">";
        }

        private int fill(String rgb) {
            if (rgb == null) return 0;
            Integer id = fills.get(rgb);
            if (id != null) return id.intValue();
            int newId = fillXml.size();
            fillXml.add("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF" + rgb
                    + "\"/><bgColor indexed=\"64\"/></patternFill></fill>");
            fills.put(rgb, Integer.valueOf(newId));
            return newId;
        }

        int idFor(Style s) {
            String key = s.key();
            Integer id = xfs.get(key);
            if (id != null) return id.intValue();
            int fontId = font(s.bold, s.fontSize, s.fontName, s.color);
            int borderId = border(s.left, s.right, s.top, s.bottom);
            int numFmtId = s.twoDecimals ? 2 : 0; // builtin 2 == "0.00"
            int fillId = fill(s.fill);
            int newId = xfDefs.size();
            xfDefs.add(new int[]{fontId, borderId, numFmtId, fillId});
            xfAlign.add(s.hAlign);
            xfs.put(key, Integer.valueOf(newId));
            return newId;
        }

        String toXml() {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            sb.append("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
            sb.append("<fonts count=\"").append(fontXml.size()).append("\">");
            for (int i = 0; i < fontXml.size(); i++) sb.append(fontXml.get(i));
            sb.append("</fonts>");
            sb.append("<fills count=\"").append(fillXml.size()).append("\">");
            for (int i = 0; i < fillXml.size(); i++) sb.append(fillXml.get(i));
            sb.append("</fills>");
            sb.append("<borders count=\"").append(borderXml.size()).append("\">");
            for (int i = 0; i < borderXml.size(); i++) sb.append(borderXml.get(i));
            sb.append("</borders>");
            sb.append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>");
            sb.append("<cellXfs count=\"").append(xfDefs.size()).append("\">");
            for (int i = 0; i < xfDefs.size(); i++) {
                int[] d = xfDefs.get(i);
                String align = xfAlign.get(i);
                sb.append("<xf numFmtId=\"").append(d[2]).append("\" fontId=\"").append(d[0])
                  .append("\" fillId=\"").append(d[3]).append("\" borderId=\"").append(d[1]).append("\" xfId=\"0\"")
                  .append(d[2] != 0 ? " applyNumberFormat=\"1\"" : "")
                  .append(d[0] != 0 ? " applyFont=\"1\"" : "")
                  .append(d[3] != 0 ? " applyFill=\"1\"" : "")
                  .append(d[1] != 0 ? " applyBorder=\"1\"" : "");
                if (align != null) {
                    sb.append(" applyAlignment=\"1\"><alignment horizontal=\"").append(align)
                      .append("\" vertical=\"center\"/></xf>");
                } else {
                    sb.append("/>");
                }
            }
            sb.append("</cellXfs>");
            sb.append("<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>");
            sb.append("</styleSheet>");
            return sb.toString();
        }
    }

    /* ------------------------------------------------------------------ */
    /* helpers                                                             */
    /* ------------------------------------------------------------------ */

    static String num(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v) && Math.abs(v) < 1e15) {
            return Long.toString((long) v);
        }
        BigDecimal bd = new BigDecimal(Double.toString(v));
        bd = bd.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
        return bd.toPlainString();
    }

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default:
                    if (ch < 0x20 && ch != '\t' && ch != '\n') break;
                    sb.append(ch);
            }
        }
        return sb.toString();
    }
}
