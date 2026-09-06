package com.toddle.bagweigher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * One truck load: the details captured on the load screen plus every bag's
 * gross weight. Loads are kept by {@link LoadStore}, which writes them to the
 * phone after every entry — a dead battery half way through a 400-bag load
 * costs nothing.
 */
public class WeighSession implements Serializable {

    private static final long serialVersionUID = 2L;

    public String id;
    public long createdAt;
    public long updatedAt;
    /** true once the sheet has been generated at least once */
    public boolean finished;

    /** sequence number of the sheet, restarts at 1 each year */
    public int seqNo;
    public String count = "";
    public String lotNo = "";
    public String vehicleNo = "";
    public String dateText = "";
    public int totalBags;
    public double tarePerBag;
    public final ArrayList<Double> grossWeights = new ArrayList<Double>();

    public WeighSession() {
        id = newId();
        createdAt = System.currentTimeMillis();
        updatedAt = createdAt;
    }

    public static String newId() {
        return Long.toString(System.currentTimeMillis(), 36)
                + Integer.toString((int) (Math.random() * 0x7FFFFFFF), 36);
    }

    /* ----------------------------------------------------------------- */
    /* derived values                                                     */
    /* ----------------------------------------------------------------- */

    public int weighedBags() {
        return grossWeights.size();
    }

    public boolean isComplete() {
        return grossWeights.size() >= totalBags;
    }

    public double totalGross() {
        double s = 0;
        for (int i = 0; i < grossWeights.size(); i++) s += grossWeights.get(i).doubleValue();
        return round(s);
    }

    public double totalTare() {
        return round(grossWeights.size() * tarePerBag);
    }

    public double totalNet() {
        return round(totalGross() - totalTare());
    }

    public double netOf(int index) {
        return round(grossWeights.get(index).doubleValue() - tarePerBag);
    }

    public static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    public List<Double> weights() {
        return grossWeights;
    }

    /** "RS · Lot 002 · GJ 05 AB 1234" */
    public String title() {
        StringBuilder sb = new StringBuilder();
        if (seqNo > 0) append(sb, "#" + seqNo);
        append(sb, count);
        if (lotNo != null && lotNo.trim().length() > 0) append(sb, "Lot " + lotNo.trim());
        append(sb, vehicleNo);
        return sb.length() == 0 ? "(no name)" : sb.toString();
    }

    private static void append(StringBuilder sb, String part) {
        if (part == null || part.trim().length() == 0) return;
        if (sb.length() > 0) sb.append("  ·  ");
        sb.append(part.trim());
    }

    /** 1_06.09.2026_4_Bags.xlsx */
    public String fileName() {
        StringBuilder sb = new StringBuilder();
        sb.append(seqNo > 0 ? String.valueOf(seqNo) : safe(count));
        if (dateText != null && dateText.trim().length() > 0) sb.append("_").append(safe(dateText));
        sb.append("_").append(totalBags).append("_Bags.xlsx");
        String name = sb.toString();
        if (name.startsWith("_")) name = name.substring(1);
        return name;
    }

    /** the year this load belongs to, taken from its date (dd.MM.yyyy) */
    public int year() {
        if (dateText != null) {
            String t = dateText.trim();
            if (t.length() >= 4) {
                try {
                    return Integer.parseInt(t.substring(t.length() - 4));
                } catch (NumberFormatException ignored) {
                    // fall through to the created date
                }
            }
        }
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(createdAt);
        return c.get(java.util.Calendar.YEAR);
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("[\\\\/:*?\"<>|]", "-").replaceAll("\\s+", "_");
    }

    /** a fresh load carrying the same details, ready to be weighed again */
    public WeighSession duplicate(String newDate) {
        WeighSession s = new WeighSession();
        s.count = count;
        s.lotNo = lotNo;
        s.vehicleNo = vehicleNo;
        s.dateText = newDate;
        s.totalBags = totalBags;
        s.tarePerBag = tarePerBag;
        return s;
    }

    /* ----------------------------------------------------------------- */
    /* json                                                               */
    /* ----------------------------------------------------------------- */

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("createdAt", createdAt);
        o.put("updatedAt", updatedAt);
        o.put("finished", finished);
        o.put("seqNo", seqNo);
        o.put("count", count);
        o.put("lotNo", lotNo);
        o.put("vehicleNo", vehicleNo);
        o.put("dateText", dateText);
        o.put("totalBags", totalBags);
        o.put("tarePerBag", tarePerBag);
        JSONArray arr = new JSONArray();
        for (int i = 0; i < grossWeights.size(); i++) arr.put(grossWeights.get(i).doubleValue());
        o.put("gross", arr);
        return o;
    }

    public static WeighSession fromJson(JSONObject o) throws JSONException {
        WeighSession s = new WeighSession();
        String storedId = o.optString("id", "");
        if (storedId.length() > 0) s.id = storedId;
        s.createdAt = o.optLong("createdAt", System.currentTimeMillis());
        s.updatedAt = o.optLong("updatedAt", s.createdAt);
        s.finished = o.optBoolean("finished", false);
        s.seqNo = o.optInt("seqNo", 0);
        s.count = o.optString("count", "");
        s.lotNo = o.optString("lotNo", "");
        s.vehicleNo = o.optString("vehicleNo", "");
        // the very first version of the app called this field "date"
        s.dateText = o.has("dateText") ? o.optString("dateText", "") : o.optString("date", "");
        s.totalBags = o.optInt("totalBags", 0);
        s.tarePerBag = o.optDouble("tarePerBag", 0);
        JSONArray arr = o.optJSONArray("gross");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                s.grossWeights.add(Double.valueOf(arr.getDouble(i)));
            }
        }
        return s;
    }
}
