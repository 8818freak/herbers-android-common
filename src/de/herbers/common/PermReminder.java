package de.herbers.common;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.List;

/**
 * Gemeinsame Erinnerung, wenn eine EINMAL erteilte Berechtigung fehlt - in
 * allen Apps gleich. Android entzieht manche Berechtigungen bei OS-Updates oder
 * automatisch ("App-Aktivitaet pausieren"); der Nutzer merkt es oft erst, wenn
 * eine Funktion still nicht mehr geht.
 *
 * <p>Grundsaetze (Mathias' Wunsch):
 * <ul>
 *   <li>Nur erinnern, wenn die Berechtigung SCHON EINMAL erteilt war (also vom
 *       Nutzer eingerichtet) - nie bei nie genutzten optionalen Funktionen.</li>
 *   <li>Die Erinnerung fuehrt mit einem Tipp direkt in die passende
 *       Berechtigungssteuerung (Einstellungen).</li>
 *   <li>Eine "Ignorieren"-Aktion, weil das Fehlen eine bewusste Entscheidung
 *       sein kann - dann wird nicht mehr genervt, bis die Berechtigung wieder
 *       erteilt (und erneut verloren) wird.</li>
 *   <li>Jede Berechtigung nur EINMAL je Verlust melden (nicht bei jeder Pruefung).</li>
 * </ul>
 *
 * <p>Die App liefert je Berechtigung ein {@link Perm} (Schluessel, Anzeigename,
 * aktueller Zustand, Einstellungs-Intent). Zum Posten braucht die App ab
 * Android 13 die Laufzeit-Berechtigung POST_NOTIFICATIONS und einen im Manifest
 * deklarierten {@link PermReminderReceiver} (fuer "Ignorieren").
 */
public final class PermReminder {

    private PermReminder() {}

    static final String PREFS = "herbers_perms";
    static final String CHANNEL = "perm_reminder";
    static final String EXTRA_KEY = "perm_key";
    static final String EXTRA_NOTIF_ID = "perm_notif_id";

    /** Eine ueberwachte Berechtigung. */
    public static final class Perm {
        final String key;        // stabiler Schluessel (z.B. "accessibility")
        final String label;      // Anzeigename (z.B. "Bedienungshilfe")
        final boolean granted;   // aktueller Zustand
        final Intent settings;   // Intent, das die Berechtigungssteuerung oeffnet
        public Perm(String key, String label, boolean granted, Intent settings) {
            this.key = key; this.label = label; this.granted = granted; this.settings = settings;
        }
    }

    private static String kOnce(String key)    { return key + "_once"; }
    private static String kIgnored(String key) { return key + "_ignored"; }
    private static int notifId(String key)     { return 0x50000000 | (key.hashCode() & 0x0FFFFFFF); }

    /** Alle uebergebenen Berechtigungen pruefen und ggf. erinnern/zuruecknehmen.
     *  Billig; darf oft aufgerufen werden (App-Start, mitgeschnittene
     *  Benachrichtigung, Job). {@code appName} erscheint im Meldungstitel. */
    public static void check(Context ctx, String appName, List<Perm> perms) {
        if (ctx == null || perms == null) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (Perm perm : perms) {
            if (perm == null || perm.key == null) continue;
            int id = notifId(perm.key);
            if (perm.granted) {
                // Erteilt: als "einmal erteilt" merken, Ignorieren zuruecksetzen,
                // eine evtl. offene Erinnerung wegnehmen.
                SharedPreferences.Editor e = p.edit().putBoolean(kOnce(perm.key), true);
                if (p.getBoolean(kIgnored(perm.key), false)) e.putBoolean(kIgnored(perm.key), false);
                e.apply();
                try { nm.cancel(id); } catch (Throwable ignored) {}
                continue;
            }
            // Fehlt: nur erinnern, wenn schon einmal erteilt und nicht ignoriert.
            if (!p.getBoolean(kOnce(perm.key), false)) continue;
            if (p.getBoolean(kIgnored(perm.key), false)) continue;
            postReminder(ctx, nm, appName, perm, id);
        }
    }

    private static void postReminder(Context ctx, NotificationManager nm, String appName,
                                     Perm perm, int id) {
        try {
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                    "Berechtigungs-Hinweise", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Hinweis, wenn eine einmal erteilte Berechtigung fehlt.");
            nm.createNotificationChannel(ch);

            Intent open = perm.settings != null ? perm.settings : new Intent(android.provider.Settings.ACTION_SETTINGS);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent openPi = PendingIntent.getActivity(ctx, id, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Intent ign = new Intent(ctx, PermReminderReceiver.class)
                    .setAction("de.herbers.common.PERM_IGNORE." + perm.key)
                    .putExtra(EXTRA_KEY, perm.key)
                    .putExtra(EXTRA_NOTIF_ID, id);
            PendingIntent ignPi = PendingIntent.getBroadcast(ctx, id, ign,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            String title = appName + ": Berechtigung fehlt";
            String text = perm.label + " ist nicht mehr erteilt – tippen zum Erteilen.";
            Notification n = new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(
                            perm.label + " war eingerichtet, ist aber nicht mehr erteilt (z. B. durch ein "
                            + "System-Update). Tippen, um sie wieder zu erteilen – oder „Ignorieren“, "
                            + "falls das gewollt ist."))
                    .setAutoCancel(true)
                    .setContentIntent(openPi)
                    .addAction(new Notification.Action.Builder(null, "Ignorieren", ignPi).build())
                    .build();
            nm.notify(id, n);
        } catch (Throwable ignored) {}
    }
}
