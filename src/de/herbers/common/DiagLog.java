package de.herbers.common;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Kleines, dauerhaftes Diagnose-Protokoll im app-internen Speicher - fuer alle
 * Apps gleich. Haelt fest, was im Hintergrund passiert und woran es haengt/
 * abstuerzt, ohne dass man ein Kabel und logcat braucht (in der App unter
 * "Diagnose-Protokoll" einsehbar/loeschbar). Bewusst winzig: nur der juengste
 * Teil bleibt erhalten (MAX_BYTES). Zuvor je App kopiert (Sucher DiagLog).
 *
 * Der logcat-Tag ist je App einstellbar ({@link #setTag}), damit sich ein Lauf
 * per Kabel auch live mitlesen laesst.
 */
public final class DiagLog {

    private DiagLog() {}

    private static final String FILE = "diag.log";
    private static final int MAX_BYTES = 128 * 1024;
    private static volatile String tag = "HerbersDiag";

    /** logcat-Tag setzen (einmal beim App-Start, z.B. "SucherDiag"). */
    public static void setTag(String t) { if (t != null && t.length() > 0) tag = t; }

    // ---- Anzeige-Schalter fuer die Log-Ansicht in den Einstellungen (in allen
    // Apps gleich). Standard AN. Eigene kleine Prefs-Datei, damit es nicht mit
    // App-Einstellungen kollidiert. ----
    private static final String PREFS = "herbers_diaglog";
    private static final String K_SHOW = "show";

    /** Ob die Log-Ansicht angezeigt werden soll (Schalter in den Einstellungen). */
    public static boolean isDisplayEnabled(Context ctx) {
        try { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(K_SHOW, true); }
        catch (Throwable t) { return true; }
    }

    public static void setDisplayEnabled(Context ctx, boolean on) {
        try { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(K_SHOW, on).apply(); }
        catch (Throwable ignored) {}
    }

    public static synchronized void log(Context ctx, String msg) {
        if (ctx == null) return;
        android.util.Log.i(tag, msg);
        String stamp = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date());
        append(ctx, stamp + "  " + msg + "\n");
    }

    public static String read(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), FILE);
            if (!f.exists()) return "";
            byte[] b = new byte[(int) f.length()];
            try (InputStream in = new FileInputStream(f)) {
                int off = 0, n;
                while (off < b.length && (n = in.read(b, off, b.length - off)) != -1) off += n;
            }
            return new String(b, "UTF-8");
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Wie {@link #read}, aber neueste Zeilen zuerst - fuer die Anzeige in den
     *  Einstellungen (Mathias' Wunsch: juengste Eintraege oben, in allen Apps). */
    public static String readNewestFirst(Context ctx) {
        String s = read(ctx);
        if (s == null || s.isEmpty()) return "";
        String[] lines = s.split("\n");
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].isEmpty()) continue;
            sb.append(lines[i]).append('\n');
        }
        return sb.toString();
    }

    public static void clear(Context ctx) {
        try { new File(ctx.getFilesDir(), FILE).delete(); } catch (Throwable ignored) {}
    }

    private static void append(Context ctx, String text) {
        try {
            String combined = read(ctx) + text;
            if (combined.length() > MAX_BYTES) {
                combined = combined.substring(combined.length() - MAX_BYTES);
            }
            File f = new File(ctx.getFilesDir(), FILE);
            try (Writer w = new OutputStreamWriter(new FileOutputStream(f, false), "UTF-8")) {
                w.write(combined);
            }
        } catch (Throwable ignored) {}
    }
}
