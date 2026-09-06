package com.toddle.bagweigher.xlsx;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * The weighment workbook.
 *
 *  - one worksheet per 100 bags, named "1-100", "101-200", ...
 *  - a header band carrying the logo, the sheet title and the load details
 *  - two blocks of 50 bags per sheet, each laid out as five column groups of
 *    ten rows: NO | GROSS | NET
 *  - a SUM() subtotal row under each block
 *  - a summary panel on the right (cols Q..U): the ten group subtotals, the
 *    sheet total, then GROSS WEIGHT (cumulative), TARE (bags x tare) and
 *    NET WEIGHT.
 *
 * Net weight of a bag is gross - tare, written as a live formula (=+B6-1.4)
 * with the computed value cached, so the numbers show in any viewer and still
 * recalculate if someone edits a gross weight.
 */
public final class BagSheetGenerator {

    /** Everything the sheet needs to know about a load. */
    public static final class Params {
        public String seqNo = "";        // "1"  -> printed as SR NO : 1
        public String count = "";        // "30" -> printed as COUNT : 30
        public String lotNo = "";
        public String vehicleNo = "";
        public String date = "";         // already formatted, e.g. 06.09.2026
        public int totalBags;
        public double tarePerBag;
        /** optional logo, PNG bytes; null for no logo */
        public byte[] logoPng;
    }

    private static final int BAGS_PER_SHEET = 100;
    private static final int BAGS_PER_BLOCK = 50;
    private static final int ROWS_PER_GROUP = 10;
    private static final int GROUPS = 5;

    // columns (1-based)
    private static final int COL_A = 1;
    private static final int COL_O = 15;
    private static final int COL_Q = 17;
    private static final int COL_R = 18;
    private static final int COL_S = 19;
    private static final int COL_T = 20;
    private static final int COL_U = 21;

    // rows
    private static final int ROW_TITLE = 1;
    private static final int ROW_INFO1 = 2;
    private static final int ROW_INFO2 = 3;
    private static final int ROW_GAP1 = 4;

    private static final int BLOCK1_HEAD = 5;
    private static final int BLOCK1_TOP = 6;      // 6..15
    private static final int BLOCK1_SUM = 16;
    private static final int ROW_GAP2 = 17;
    private static final int BLOCK2_HEAD = 18;
    private static final int BLOCK2_TOP = 19;     // 19..28
    private static final int BLOCK2_SUM = 29;

    private static final int PANEL_HEAD = 5;
    private static final int PANEL_FIRST = 6;     // 6..15
    private static final int PANEL_TOTAL = 16;
    private static final int PANEL_GROSS = 18;
    private static final int PANEL_TARE = 19;
    private static final int PANEL_NET = 20;
    private static final int PANEL_CHECK = 22;

    private static final String THIN = "thin";
    private static final String MEDIUM = "medium";

    // colours
    private static final String FILL_HEAD = "14506F";      // column headers, dark blue
    private static final String FILL_BAGNO = "DCEEF6";     // bag-number columns
    private static final String FILL_SUBTOTAL = "F2DCDB";  // subtotal rows
    private static final String FILL_INFO = "F2F5F7";      // the detail cells up top
    private static final String FILL_NET = "FDE9D9";       // the final net weight

    private BagSheetGenerator() { }

    /**
     * @param grossWeights one entry per weighed bag, in bag order. May be
     *                     shorter than params.totalBags (partial load).
     */
    public static void write(Params params, List<Double> grossWeights, OutputStream out) throws IOException {
        XlsxWriter wb = new XlsxWriter();
        int sheetCount = Math.max(1, (params.totalBags + BAGS_PER_SHEET - 1) / BAGS_PER_SHEET);

        String prevSheetName = null;
        for (int sheetIdx = 0; sheetIdx < sheetCount; sheetIdx++) {
            int firstBag = sheetIdx * BAGS_PER_SHEET + 1;
            String name = firstBag + "-" + (firstBag + BAGS_PER_SHEET - 1);
            XlsxWriter.Sheet sh = wb.addSheet(name);
            buildSheet(sh, params, grossWeights, sheetIdx, prevSheetName);
            prevSheetName = name;
        }
        wb.write(out);
    }

    /* ------------------------------------------------------------------ */

    private static void buildSheet(XlsxWriter.Sheet sh, Params p, List<Double> gross,
                                   int sheetIdx, String prevSheet) {
        page(sh);
        header(sh, p);

        int firstBag = sheetIdx * BAGS_PER_SHEET + 1;
        double[] gTot = new double[10];
        double[] nTot = new double[10];

        boolean[] used = new boolean[10];

        blockHeader(sh, BLOCK1_HEAD);
        block(sh, p, gross, firstBag, BLOCK1_TOP, BLOCK1_SUM, 0, gTot, nTot, used);

        blockHeader(sh, BLOCK2_HEAD);
        block(sh, p, gross, firstBag + BAGS_PER_BLOCK, BLOCK2_TOP, BLOCK2_SUM, 5, gTot, nTot, used);

        summaryPanel(sh, p, sheetIdx, prevSheet, gTot, nTot, used, gross);
    }

    private static void page(XlsxWriter.Sheet sh) {
        double[] widths = {4.6, 9.2, 9.2, 4.6, 9.2, 9.2, 4.6, 9.2, 9.2, 4.6, 9.2, 9.2,
                           4.6, 9.2, 9.2, 2.4, 10.4, 6.0, 6.0, 10.4, 2.6};
        for (int i = 0; i < widths.length; i++) sh.colWidth(i + 1, widths[i]);

        sh.rowHeight(ROW_TITLE, 51);
        sh.rowHeight(ROW_INFO1, 19.5);
        sh.rowHeight(ROW_INFO2, 19.5);
        sh.rowHeight(ROW_GAP1, 7.5);
        sh.rowHeight(ROW_GAP2, 7.5);
        for (int r = BLOCK1_HEAD; r <= BLOCK2_SUM; r++) {
            if (r != ROW_GAP2) sh.rowHeight(r, 18.75);
        }
        sh.rowHeight(BLOCK1_HEAD, 21);
        sh.rowHeight(BLOCK2_HEAD, 21);

        sh.gridLines(false);
        sh.fitToOnePageWide();
    }

    /* ---------------- header band ---------------- */

    private static void header(XlsxWriter.Sheet sh, Params p) {
        if (p.logoPng != null && p.logoPng.length > 0) {
            // 3.44 : 1 — the proportions of the supplied logo
            sh.image(p.logoPng, "png", ROW_TITLE, COL_A, 205, 60);
        }

        XlsxWriter.Style title = XlsxWriter.style().bold(true).font("Calibri", 20).align("center");
        sh.str(ROW_TITLE, 6, "WEIGHMENT SHEET", title);
        sh.merge(ROW_TITLE, 6, ROW_TITLE, COL_O);

        XlsxWriter.Style stamp = XlsxWriter.style().bold(true).font("Calibri", 10).align("right");
        sh.str(ROW_TITLE, COL_Q, "DATE : " + nz(p.date), stamp);
        sh.merge(ROW_TITLE, COL_Q, ROW_TITLE, COL_U);

        info(sh, ROW_INFO1, COL_A, 3, "SR NO : " + nz(p.seqNo));
        info(sh, ROW_INFO1, 4, 6, "COUNT : " + nz(p.count));
        info(sh, ROW_INFO1, 7, 9, "LOT NO : " + nz(p.lotNo));
        info(sh, ROW_INFO1, 10, COL_O, "VEHICLE NO : " + nz(p.vehicleNo));
        info(sh, ROW_INFO1, COL_Q, COL_U, "TOTAL BAGS : " + p.totalBags);

        info(sh, ROW_INFO2, COL_A, 3, "TARE / BAG : " + two(p.tarePerBag));
        info(sh, ROW_INFO2, 4, 6, "NET = GROSS − TARE");
        info(sh, ROW_INFO2, 7, COL_O, "");
        info(sh, ROW_INFO2, COL_Q, COL_U, "WEIGHTS IN KG");
    }

    private static void info(XlsxWriter.Sheet sh, int row, int c1, int c2, String text) {
        XlsxWriter.Style s = XlsxWriter.style().bold(true).font("Calibri", 10.5)
                .fill(FILL_INFO).borders(THIN, THIN, THIN, THIN).align("left");
        sh.str(row, c1, text, s);
        for (int c = c1 + 1; c <= c2; c++) sh.blank(row, c, s);
        if (c2 > c1) sh.merge(row, c1, row, c2);
    }

    /* ---------------- the bag blocks ---------------- */

    private static void blockHeader(XlsxWriter.Sheet sh, int row) {
        XlsxWriter.Style head = XlsxWriter.style().bold(true).font("Calibri", 9.5)
                .fill(FILL_HEAD).color("FFFFFF").align("center").borders(THIN, THIN, THIN, THIN);
        for (int g = 0; g < GROUPS; g++) {
            int c = 1 + g * 3;
            sh.str(row, c, "NO", head);
            sh.str(row, c + 1, "GROSS", head);
            sh.str(row, c + 2, "NET", head.borders(THIN, MEDIUM, THIN, THIN));
        }
    }

    private static void block(XlsxWriter.Sheet sh, Params p, List<Double> gross,
                              int firstBagOfBlock, int topRow, int sumRow, int groupOffset,
                              double[] grossTotals, double[] netTotals, boolean[] used) {

        XlsxWriter.Style bagNo = XlsxWriter.style().bold(true).font("Calibri", 10)
                .fill(FILL_BAGNO).align("center").borders(THIN, THIN, THIN, THIN);
        XlsxWriter.Style weight = XlsxWriter.style().decimals(true).align("right")
                .borders(THIN, THIN, THIN, THIN);
        XlsxWriter.Style weightEnd = weight.borders(THIN, MEDIUM, THIN, THIN);
        XlsxWriter.Style sub = XlsxWriter.style().bold(true).font("Calibri", 11).decimals(true)
                .align("right").fill(FILL_SUBTOTAL).borders(THIN, THIN, THIN, THIN);
        XlsxWriter.Style subEnd = sub.borders(THIN, MEDIUM, THIN, THIN);
        XlsxWriter.Style subLabel = XlsxWriter.style().bold(true).font("Calibri", 8)
                .fill(FILL_SUBTOTAL).align("center").borders(THIN, THIN, THIN, THIN);

        for (int g = 0; g < GROUPS; g++) {
            int cNo = 1 + g * 3, cG = cNo + 1, cN = cNo + 2;
            double gSum = 0, nSum = 0;
            boolean any = false;

            for (int i = 0; i < ROWS_PER_GROUP; i++) {
                int row = topRow + i;
                int bagNumber = firstBagOfBlock + g * ROWS_PER_GROUP + i;

                sh.num(row, cNo, bagNumber, bagNo);

                Double w = valueFor(gross, bagNumber);
                if (w != null) {
                    double net = clean(w.doubleValue() - p.tarePerBag);
                    sh.num(row, cG, w.doubleValue(), weight);
                    sh.formula(row, cN,
                            "+" + XlsxWriter.ref(row, cG) + "-" + trim(p.tarePerBag),
                            Double.valueOf(net), weightEnd);
                    gSum += w.doubleValue();
                    nSum += net;
                    any = true;
                } else {
                    sh.blank(row, cG, weight);
                    sh.blank(row, cN, weightEnd);
                }
            }

            gSum = clean(gSum);
            nSum = clean(nSum);
            grossTotals[groupOffset + g] = gSum;
            netTotals[groupOffset + g] = nSum;
            used[groupOffset + g] = any;

            // a group nobody weighed stays empty rather than showing 0.00
            sh.str(sumRow, cNo, any ? "TOT" : "", subLabel);
            if (any) {
                sh.formula(sumRow, cG,
                        "SUM(" + XlsxWriter.ref(topRow, cG) + ":" + XlsxWriter.ref(topRow + 9, cG) + ")",
                        Double.valueOf(gSum), sub);
                sh.formula(sumRow, cN,
                        "SUM(" + XlsxWriter.ref(topRow, cN) + ":" + XlsxWriter.ref(topRow + 9, cN) + ")",
                        Double.valueOf(nSum), subEnd);
            } else {
                sh.blank(sumRow, cG, sub);
                sh.blank(sumRow, cN, subEnd);
            }
        }
    }

    /* ---------------- summary panel ---------------- */

    private static void summaryPanel(XlsxWriter.Sheet sh, Params p, int sheetIdx, String prevSheet,
                                     double[] gTot, double[] nTot, boolean[] used, List<Double> gross) {

        XlsxWriter.Style head = XlsxWriter.style().bold(true).font("Calibri", 9.5)
                .fill(FILL_HEAD).color("FFFFFF").align("center").borders(THIN, THIN, THIN, THIN);
        sh.str(PANEL_HEAD, COL_Q, "BAGS", head);
        sh.str(PANEL_HEAD, COL_R, "GROSS", head);
        sh.blank(PANEL_HEAD, COL_S, head);
        sh.merge(PANEL_HEAD, COL_R, PANEL_HEAD, COL_S);
        sh.str(PANEL_HEAD, COL_T, "NET", head);
        sh.blank(PANEL_HEAD, COL_U, head);
        sh.merge(PANEL_HEAD, COL_T, PANEL_HEAD, COL_U);

        XlsxWriter.Style range = XlsxWriter.style().font("Calibri", 10).align("center")
                .fill(FILL_BAGNO).borders(THIN, THIN, THIN, THIN);
        XlsxWriter.Style value = XlsxWriter.style().decimals(true).align("right")
                .borders(THIN, THIN, THIN, THIN);
        XlsxWriter.Style valueBold = value.bold(true);

        int firstBag = sheetIdx * BAGS_PER_SHEET + 1;
        for (int i = 0; i < 10; i++) {
            int row = PANEL_FIRST + i;
            int from = firstBag + i * 10;
            sh.str(row, COL_Q, from + "-" + (from + 9), range);

            int srcRow = (i < 5) ? BLOCK1_SUM : BLOCK2_SUM;
            int grp = (i < 5) ? i : i - 5;

            if (used[i]) {
                sh.formula(row, COL_R, "+" + XlsxWriter.ref(srcRow, 2 + grp * 3),
                        Double.valueOf(gTot[i]), value);
            } else {
                sh.blank(row, COL_R, value);
            }
            sh.blank(row, COL_S, value);
            sh.merge(row, COL_R, row, COL_S);

            if (used[i]) {
                sh.formula(row, COL_T, "+" + XlsxWriter.ref(srcRow, 3 + grp * 3),
                        Double.valueOf(nTot[i]), valueBold);
            } else {
                sh.blank(row, COL_T, valueBold);
            }
            sh.blank(row, COL_U, valueBold);
            sh.merge(row, COL_T, row, COL_U);
        }

        double sheetGross = 0, sheetNet = 0;
        for (int i = 0; i < 10; i++) { sheetGross += gTot[i]; sheetNet += nTot[i]; }
        sheetGross = clean(sheetGross);
        sheetNet = clean(sheetNet);

        XlsxWriter.Style totalLabel = XlsxWriter.style().bold(true).font("Calibri", 9.5)
                .fill(FILL_SUBTOTAL).align("center").borders(THIN, THIN, THIN, THIN);
        XlsxWriter.Style total = XlsxWriter.style().bold(true).font("Calibri", 11).decimals(true)
                .align("right").fill(FILL_SUBTOTAL).borders(THIN, THIN, THIN, THIN);

        sh.str(PANEL_TOTAL, COL_Q, "TOTAL", totalLabel);
        sh.formula(PANEL_TOTAL, COL_R,
                "SUM(" + XlsxWriter.ref(PANEL_FIRST, COL_R) + ":" + XlsxWriter.ref(PANEL_FIRST + 9, COL_S) + ")",
                Double.valueOf(sheetGross), total);
        sh.blank(PANEL_TOTAL, COL_S, total);
        sh.merge(PANEL_TOTAL, COL_R, PANEL_TOTAL, COL_S);
        sh.formula(PANEL_TOTAL, COL_T,
                "SUM(" + XlsxWriter.ref(PANEL_FIRST, COL_T) + ":" + XlsxWriter.ref(PANEL_FIRST + 9, COL_T) + ")",
                Double.valueOf(sheetNet), total);
        sh.blank(PANEL_TOTAL, COL_U, total);
        sh.merge(PANEL_TOTAL, COL_T, PANEL_TOTAL, COL_U);

        /* ---- cumulative gross / tare / net ---- */

        int bagsSoFar = Math.min((sheetIdx + 1) * BAGS_PER_SHEET, p.totalBags);
        double cumGross = clean(sumGross(gross, (sheetIdx + 1) * BAGS_PER_SHEET));
        double cumNet = clean(sumNet(gross, (sheetIdx + 1) * BAGS_PER_SHEET, p.tarePerBag));
        double totalTare = clean(bagsSoFar * p.tarePerBag);

        String grossFormula = (prevSheet == null)
                ? "+" + XlsxWriter.ref(PANEL_TOTAL, COL_R)
                : "+" + XlsxWriter.ref(PANEL_TOTAL, COL_R) + "+'" + prevSheet + "'!" + XlsxWriter.ref(PANEL_GROSS, COL_T);

        bigRow(sh, PANEL_GROSS, "GROSS WEIGHT", grossFormula, cumGross, null);

        // tare: bags x tare per bag, spelled out so the sheet shows its working
        XlsxWriter.Style tareLabel = XlsxWriter.style().bold(true).font("Calibri", 10.5)
                .align("left").borders(MEDIUM, THIN, THIN, THIN);
        XlsxWriter.Style tareCell = XlsxWriter.style().bold(true).font("Calibri", 10.5)
                .align("center").borders(THIN, THIN, THIN, THIN);
        XlsxWriter.Style tareValue = XlsxWriter.style().bold(true).font("Calibri", 12).decimals(true)
                .align("right").borders(THIN, MEDIUM, THIN, THIN);

        sh.str(PANEL_TARE, COL_Q, "TARE", tareLabel);
        sh.num(PANEL_TARE, COL_R, bagsSoFar, tareCell);
        sh.num(PANEL_TARE, COL_S, p.tarePerBag, tareCell);
        sh.formula(PANEL_TARE, COL_T,
                XlsxWriter.ref(PANEL_TARE, COL_R) + "*" + XlsxWriter.ref(PANEL_TARE, COL_S),
                Double.valueOf(totalTare), tareValue);
        sh.blank(PANEL_TARE, COL_U, tareValue);
        sh.merge(PANEL_TARE, COL_T, PANEL_TARE, COL_U);

        bigRow(sh, PANEL_NET, "NET WEIGHT",
                XlsxWriter.ref(PANEL_GROSS, COL_T) + "-" + XlsxWriter.ref(PANEL_TARE, COL_T),
                clean(cumGross - totalTare), FILL_NET);

        // cross-check: the same net, added up from the group subtotals instead
        String checkFormula = (prevSheet == null)
                ? "+" + XlsxWriter.ref(PANEL_TOTAL, COL_T)
                : "+" + XlsxWriter.ref(PANEL_TOTAL, COL_T) + "+'" + prevSheet + "'!" + XlsxWriter.ref(PANEL_CHECK, COL_T);

        XlsxWriter.Style checkLabel = XlsxWriter.style().font("Calibri", 9).align("left");
        XlsxWriter.Style checkValue = XlsxWriter.style().font("Calibri", 9).decimals(true).align("right");
        sh.str(PANEL_CHECK, COL_Q, "net from subtotals", checkLabel);
        sh.blank(PANEL_CHECK, COL_R, checkLabel);
        sh.merge(PANEL_CHECK, COL_Q, PANEL_CHECK, COL_S);
        sh.formula(PANEL_CHECK, COL_T, checkFormula, Double.valueOf(cumNet), checkValue);
        sh.merge(PANEL_CHECK, COL_T, PANEL_CHECK, COL_U);
    }

    /** one of the big GROSS / NET rows: label across Q..S, value across T..U */
    private static void bigRow(XlsxWriter.Sheet sh, int row, String label,
                               String formula, double cached, String fill) {
        XlsxWriter.Style labelStyle = XlsxWriter.style().bold(true).font("Calibri", 11)
                .align("left").borders(MEDIUM, THIN, THIN, THIN);
        XlsxWriter.Style pad = XlsxWriter.style().borders(THIN, THIN, THIN, THIN);
        XlsxWriter.Style valueStyle = XlsxWriter.style().bold(true).font("Calibri", 13).decimals(true)
                .align("right").borders(THIN, MEDIUM, THIN, THIN);
        if (fill != null) {
            labelStyle = labelStyle.fill(fill);
            pad = pad.fill(fill);
            valueStyle = valueStyle.fill(fill);
        }

        sh.str(row, COL_Q, label, labelStyle);
        sh.blank(row, COL_R, pad);
        sh.blank(row, COL_S, pad);
        sh.merge(row, COL_Q, row, COL_S);
        sh.formula(row, COL_T, formula, Double.valueOf(cached), valueStyle);
        sh.blank(row, COL_U, valueStyle);
        sh.merge(row, COL_T, row, COL_U);
    }

    /* ------------------------------------------------------------------ */

    private static Double valueFor(List<Double> gross, int bagNumber) {
        if (gross == null || bagNumber < 1 || bagNumber > gross.size()) return null;
        return gross.get(bagNumber - 1);
    }

    private static double sumGross(List<Double> gross, int uptoBag) {
        if (gross == null) return 0;
        double s = 0;
        int n = Math.min(uptoBag, gross.size());
        for (int i = 0; i < n; i++) {
            Double d = gross.get(i);
            if (d != null) s += d.doubleValue();
        }
        return s;
    }

    private static double sumNet(List<Double> gross, int uptoBag, double tare) {
        if (gross == null) return 0;
        double s = 0;
        int n = Math.min(uptoBag, gross.size());
        for (int i = 0; i < n; i++) {
            Double d = gross.get(i);
            if (d != null) s += clean(d.doubleValue() - tare);
        }
        return s;
    }

    /** kills binary-float noise without touching the precision the user typed */
    public static double clean(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** 1.40 -> "1.4", 1.00 -> "1" : how the tare reads inside a formula */
    static String trim(double v) {
        if (v == Math.rint(v)) return Long.toString((long) v);
        String s = String.valueOf(clean(v));
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s;
    }

    /** 1.4 -> "1.40" : how the tare reads in the header band */
    static String two(double v) {
        return String.format(java.util.Locale.US, "%.2f", Double.valueOf(v));
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
