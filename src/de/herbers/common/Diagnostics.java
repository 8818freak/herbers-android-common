package de.herbers.common;

import android.content.Context;

/**
 * Gemeinsame Diagnose-Helfer - in allen Apps gleich:
 * - {@link #stackOf(Thread, int)}: den Stack eines (haengenden) Threads
 *   formatieren; zeigt die genaue blockierende Stelle, ohne Debug-Build/Root.
 * - {@link #stackOf(Throwable)}: kompletten Fehler-Stack als Text.
 * - {@link #installCrashLogger(Context)}: unbehandelte Abstuerze mit vollem
 *   Stack ins {@link DiagLog} schreiben, danach regulaer weiterreichen.
 *
 * Der (app-spezifische) Watchdog um einen langlaufenden Arbeiter bleibt in der
 * jeweiligen App - nur der wiederverwendbare Kern (Stack festhalten,
 * Absturz protokollieren) liegt hier.
 */
public final class Diagnostics {

    private Diagnostics() {}

    /** Formatierter Stack eines Threads (bis maxFrames Zeilen, <=0 = alle). */
    public static String stackOf(Thread t, int maxFrames) {
        if (t == null) return "(kein Thread)";
        try {
            StackTraceElement[] st = t.getStackTrace();
            if (st == null || st.length == 0) return "(leerer Stack)";
            int n = (maxFrames <= 0) ? st.length : Math.min(st.length, maxFrames);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) sb.append("\n    at ").append(st[i]);
            return sb.toString();
        } catch (Throwable e) {
            return "(Stack nicht lesbar: " + e + ")";
        }
    }

    /** Kompletter Stacktrace eines Fehlers (inkl. Ursachen-Kette) als String. */
    public static String stackOf(Throwable err) {
        if (err == null) return "(kein Fehler)";
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            err.printStackTrace(new java.io.PrintWriter(sw));
            return sw.toString();
        } catch (Throwable e) {
            return String.valueOf(err);
        }
    }

    /**
     * Installiert einen Absturz-Logger: unbehandelte Ausnahmen werden mit vollem
     * Stack ins {@link DiagLog} geschrieben und danach an den bisherigen Handler
     * weitergereicht (die App stuerzt regulaer ab / zeigt den Systemdialog).
     * Nur einmal je Prozess aufrufen (z.B. in Application/Service onCreate).
     * Der Application-Context wird gehalten - deshalb getApplicationContext.
     */
    public static void installCrashLogger(Context ctx) {
        if (ctx == null) return;
        final Context app = ctx.getApplicationContext();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable err) {
                try {
                    DiagLog.log(app, "ABSTURZ im Thread »" + (t == null ? "?" : t.getName())
                            + "«:\n" + stackOf(err));
                } catch (Throwable ignored) {}
                if (prev != null) prev.uncaughtException(t, err);
            }
        });
    }
}
