package de.herbers.common;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/**
 * Gemeinsamer Kern fuer das Mitschneiden von System-Benachrichtigungen. Reine,
 * statische Helfer auf {@link Notification}/{@link StatusBarNotification} ohne
 * Kopplung an App-Klassen (Store/UI) - damit die vier Apps (EdgeTab, Sucher,
 * ActiveFrames, BBMePing) Feld-Extraktion, Filter und Aktions-Erkennung gleich
 * aufgebaut nutzen. Zuvor je App leicht abweichend kopiert; Referenz war
 * EdgeTabs NotificationCollector.
 *
 * Kein Sonderrecht noetig: jeder aktive NotificationListenerService darf diese
 * Felder lesen und die enthaltenen Aktions-PendingIntents ausloesen.
 */
public final class Notifications {

    private Notifications() {}

    /** Trennt Inhalts-Kennungen (App/Titel/Text) - unwahrscheinlich in echten Texten. */
    private static final String SEP = "";

    /**
     * Titel und Text so auslesen, wie eine Benachrichtigung sie anzeigt: Titel
     * aus EXTRA_TITLE, Text aus EXTRA_TEXT - aber den laengeren EXTRA_BIG_TEXT
     * bevorzugen (aufgeklappte Ansicht). Liefert immer ein 2er-Array
     * {Titel, Text}, nie null-Elemente (leer statt null).
     */
    public static String[] titleAndText(Notification n) {
        Bundle ex = n == null ? null : n.extras;
        CharSequence title = ex == null ? null : ex.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text  = ex == null ? null : ex.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence big   = ex == null ? null : ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (big != null && (text == null || big.length() > text.length())) text = big;
        return new String[]{ title == null ? "" : title.toString(),
                             text  == null ? "" : text.toString() };
    }

    /** Reine Gruppen-Zusammenfassung ("3 neue Mails") ohne Einzelinhalt. */
    public static boolean isGroupSummary(Notification n) {
        return n != null && (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
    }

    /** Weder Titel noch Text - nichts Anzeigbares/Durchsuchbares. */
    public static boolean isBlank(String title, String text) {
        return (title == null || title.isEmpty()) && (text == null || text.isEmpty());
    }

    /**
     * Inhaltliche Kennung: gleiche App + Titel + Text = dieselbe Nachricht.
     * Noetig, weil manche Quellen (z.B. der BlackBerry Hub) dieselbe Mail
     * mehrfach neu posten und dabei den Schluessel wechseln.
     */
    public static String signature(String pkg, String title, String text) {
        return (pkg == null ? "" : pkg) + SEP
                + (title == null ? "" : title) + SEP
                + (text == null ? "" : text);
    }

    /** Lesbarer App-Name zum Paketnamen (sonst der Paketname selbst). */
    public static String appLabel(Context ctx, String pkg) {
        if (ctx == null || pkg == null) return pkg;
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence l = pm.getApplicationLabel(ai);
            if (l != null && l.length() > 0) return l.toString();
        } catch (Exception ignored) {}
        return pkg;
    }

    /**
     * Eine "echte", einen Hinweis rechtfertigende Einzel-Benachrichtigung.
     * Ausgeschlossen: dauerhafte/nicht wegwischbare Meldungen (laufende
     * Dienste), Gruppen-Sammelmeldungen und Dienst-/Transport-/Fortschritts-/
     * System-Kategorien (Sync, Mediensteuerung, Downloads ...).
     */
    public static boolean isReal(StatusBarNotification sbn) {
        if (sbn == null) return false;
        Notification n = sbn.getNotification();
        if (n == null) return false;
        String cat = n.category;
        boolean noise = Notification.CATEGORY_SERVICE.equals(cat)
                || Notification.CATEGORY_TRANSPORT.equals(cat)
                || Notification.CATEGORY_PROGRESS.equals(cat)
                || Notification.CATEGORY_SYSTEM.equals(cat);
        return sbn.isClearable() && !isGroupSummary(n) && !noise;
    }

    // --- Aktionen einer Benachrichtigung (wie die Knoepfe in der System-
    //     Benachrichtigung selbst) --------------------------------------------

    /** Alle Aktionen inkl. Wearable-Aktionen einsammeln. */
    private static ArrayList<Notification.Action> allActions(Notification n) {
        ArrayList<Notification.Action> all = new ArrayList<>();
        if (n == null) return all;
        if (n.actions != null) Collections.addAll(all, n.actions);
        try { all.addAll(new Notification.WearableExtender(n).getActions()); }
        catch (Exception ignored) {}
        return all;
    }

    /**
     * "Antworten"-Aktion mit freier Texteingabe (RemoteInput) - was Messenger
     * (Telegram, Signal/Molly, WhatsApp ...) fuer die Direktantwort aus der
     * Benachrichtigung mitliefern.
     */
    public static Notification.Action findReplyAction(Notification n) {
        if (n == null || n.actions == null) return null;
        for (Notification.Action a : n.actions) {
            if (a == null || a.actionIntent == null || a.getRemoteInputs() == null) continue;
            for (RemoteInput ri : a.getRemoteInputs()) {
                if (ri != null && ri.getAllowFreeFormInput()) return a;
            }
        }
        return null;
    }

    /**
     * Wie {@link #findReplyAction}, aber auch fuer "Antworten"-Aktionen OHNE
     * RemoteInput (z.B. BlackBerry Hubs "Allen antworten" als reine
     * startActivity-Aktion). Erkennung: erst RemoteInput, dann semantisch
     * (SEMANTIC_ACTION_REPLY), dann per Beschriftung.
     */
    public static Notification.Action findAnyReplyAction(Notification n) {
        Notification.Action withInput = findReplyAction(n);
        if (withInput != null) return withInput;
        if (n == null || n.actions == null) return null;
        for (Notification.Action a : n.actions) {
            if (a != null && a.actionIntent != null
                    && a.getSemanticAction() == Notification.Action.SEMANTIC_ACTION_REPLY) {
                return a;
            }
        }
        for (Notification.Action a : n.actions) {
            if (a == null || a.actionIntent == null || a.title == null) continue;
            String t = a.title.toString().toLowerCase(Locale.ROOT);
            if (t.contains("antwort") || t.contains("reply")) return a;
        }
        return null;
    }

    /**
     * "Loeschen"-Aktion (auch Wearable-Aktionen): erst semantisch
     * (SEMANTIC_ACTION_DELETE), dann per Beschriftung.
     */
    public static PendingIntent findDeleteAction(Notification n) {
        ArrayList<Notification.Action> all = allActions(n);
        for (Notification.Action a : all) {
            if (a != null && a.actionIntent != null
                    && a.getSemanticAction() == Notification.Action.SEMANTIC_ACTION_DELETE) {
                return a.actionIntent;
            }
        }
        for (Notification.Action a : all) {
            if (a == null || a.actionIntent == null || a.title == null) continue;
            String t = a.title.toString().toLowerCase(Locale.ROOT);
            if (t.contains("lösch") || t.contains("losch") || t.contains("delete")
                    || t.contains("papierkorb") || t.contains("trash")) {
                return a.actionIntent;
            }
        }
        return null;
    }

    /**
     * "Als gelesen markieren"-Aktion (auch Wearable-Aktionen): erst semantisch
     * (SEMANTIC_ACTION_MARK_AS_READ), dann per Beschriftung.
     */
    public static PendingIntent findMarkReadAction(Notification n) {
        ArrayList<Notification.Action> all = allActions(n);
        for (Notification.Action a : all) {
            if (a != null && a.actionIntent != null
                    && a.getSemanticAction() == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ) {
                return a.actionIntent;
            }
        }
        for (Notification.Action a : all) {
            if (a == null || a.actionIntent == null || a.title == null) continue;
            String t = a.title.toString().toLowerCase(Locale.ROOT);
            if (t.contains("gelesen") || t.contains("mark as read") || t.contains("mark read")) {
                return a.actionIntent;
            }
        }
        return null;
    }

    /**
     * Fuellt einen fill-in-Intent fuer die RemoteInput-Aktion mit dem
     * Antworttext - danach mit action.actionIntent (PendingIntent) senden.
     * Gibt null zurueck, wenn die Aktion keine freie Texteingabe hat.
     */
    public static android.content.Intent buildReplyFillIn(Notification.Action action, String text) {
        if (action == null || action.getRemoteInputs() == null) return null;
        Bundle results = new Bundle();
        boolean any = false;
        for (RemoteInput ri : action.getRemoteInputs()) {
            if (ri != null && ri.getAllowFreeFormInput()) {
                results.putCharSequence(ri.getResultKey(), text);
                any = true;
            }
        }
        if (!any) return null;
        android.content.Intent fillIn = new android.content.Intent();
        RemoteInput.addResultsToIntent(action.getRemoteInputs(), fillIn, results);
        return fillIn;
    }
}
