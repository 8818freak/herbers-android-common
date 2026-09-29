package de.herbers.common;

import android.app.Notification;
import android.app.PendingIntent;
import android.service.notification.StatusBarNotification;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Haelt die "Draehte" (PendingIntents/Aktionen) noch bzw. zuletzt gesehener
 * System-Benachrichtigungen fest, damit eine App sie ausloesen kann, AUCH wenn
 * die Benachrichtigung schon aus der Statusleiste verschwunden ist - z.B.
 * antworten, nachdem eine gelesene Nachricht weggewischt wurde.
 *
 * Gemeinsamer Kern fuer die vier Apps (zuvor hielt EdgeTabs NotificationCollector
 * contentIntent/Loeschen in eigenen Maps): eine App ruft beim Eintreffen
 * {@link #remember(StatusBarNotification)} und liest spaeter die gemerkten
 * Draehte je Schluessel wieder aus.
 *
 * WICHTIG - Grenzen (bewusst, entsprechen der Natur eines PendingIntent, der ein
 * lebendes IPC-Token ist, kein speicherbarer Wert):
 *  - Nur im RAM. Ein Systemneustart oder das Beenden DIESES Prozesses leert die
 *    Ablage - sie laesst sich nicht auf Platte sichern.
 *  - Wird die Quell-App aktualisiert, beendet oder stuerzt sie ab, werden ihre
 *    PendingIntents ungueltig; ein gemerkter Draht kann dann still ins Leere
 *    laufen. Aufrufer sollten daher zuerst den frischen Draht der noch lebenden
 *    Benachrichtigung versuchen und den gemerkten nur als Rueckfall nehmen -
 *    und bei Fehlschlag einen weiteren Rueckfall haben (z.B. die App oeffnen).
 *  - Manche Apps ziehen den Antwort-Draht zurueck, sobald der Chat als gelesen
 *    gilt. Auch dann verpufft ein gemerkter Draht - kein Fehler dieser Ablage.
 *
 * Reine statische Ablage ohne App-Kopplung. Bilder/Extras gehoeren nicht hierher
 * (siehe {@link Notifications}).
 */
public final class NotificationActionCache {

    private NotificationActionCache() {}

    /** Die gemerkten Draehte einer Benachrichtigung. Felder koennen null sein
     *  (nicht jede Benachrichtigung bietet jede Aktion). */
    public static final class Wires {
        public final PendingIntent contentIntent;   // Antippen -> konkrete Ansicht oeffnen
        public final PendingIntent deleteAction;    // "Loeschen"-Aktion (loescht in der Quell-App)
        public final Notification.Action replyAction; // "Antworten"-Aktion (Freitext oder Activity)
        public final PendingIntent markReadAction;  // "Als gelesen markieren"-Aktion
        public final long postTime;
        public Wires(PendingIntent content, PendingIntent delete, Notification.Action reply,
                     PendingIntent markRead, long postTime) {
            this.contentIntent = content;
            this.deleteAction = delete;
            this.replyAction = reply;
            this.markReadAction = markRead;
            this.postTime = postTime;
        }
    }

    private static final ConcurrentHashMap<String, Wires> BY_KEY = new ConcurrentHashMap<>();

    /** Alle Draehte einer Benachrichtigung merken (unter ihrem Schluessel).
     *  Ueberschreibt einen vorhandenen Eintrag desselben Schluessels mit dem
     *  frischeren - Apps erneuern ihre PendingIntents oft. */
    public static void remember(StatusBarNotification sbn) {
        if (sbn == null || sbn.getKey() == null) return;
        Notification n = sbn.getNotification();
        if (n == null) return;
        PendingIntent content = n.contentIntent;
        PendingIntent delete = Notifications.findDeleteAction(n);
        Notification.Action reply = Notifications.findAnyReplyAction(n);
        PendingIntent markRead = Notifications.findMarkReadAction(n);
        // Nichts Nuetzliches -> keinen leeren Eintrag anlegen.
        if (content == null && delete == null && reply == null && markRead == null) return;
        BY_KEY.put(sbn.getKey(), new Wires(content, delete, reply, markRead, sbn.getPostTime()));
    }

    /** Die gemerkten Draehte zu einem Schluessel (oder null). */
    public static Wires get(String key) {
        return key == null ? null : BY_KEY.get(key);
    }

    /** Nur die Antwort-Aktion zu einem Schluessel (oder null). */
    public static Notification.Action replyAction(String key) {
        Wires w = get(key);
        return w == null ? null : w.replyAction;
    }

    /** Nur den contentIntent zu einem Schluessel (oder null). */
    public static PendingIntent contentIntent(String key) {
        Wires w = get(key);
        return w == null ? null : w.contentIntent;
    }

    /** Nur die Loeschen-Aktion zu einem Schluessel (oder null). */
    public static PendingIntent deleteAction(String key) {
        Wires w = get(key);
        return w == null ? null : w.deleteAction;
    }

    /** Nur die "Als gelesen markieren"-Aktion zu einem Schluessel (oder null). */
    public static PendingIntent markReadAction(String key) {
        Wires w = get(key);
        return w == null ? null : w.markReadAction;
    }

    /** Einen Eintrag vergessen (z.B. nach erfolgreichem Loeschen). */
    public static void forget(String key) {
        if (key != null) BY_KEY.remove(key);
    }

    /** Alle gemerkten Schluessel (Kopie). */
    public static java.util.Set<String> keys() {
        return new java.util.HashSet<>(BY_KEY.keySet());
    }

    /** Ablage leeren (z.B. beim Trennen des Listener-Dienstes). */
    public static void clear() {
        BY_KEY.clear();
    }
}
