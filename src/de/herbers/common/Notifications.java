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

    /**
     * Reichhaltigerer Text als {@link #titleAndText}: bezieht die Mehrzeiler-
     * und Chat-Darstellungen mit ein, die viele Apps zusaetzlich mitliefern -
     * InboxStyle-Zeilen (EXTRA_TEXT_LINES, z.B. mehrere Mails), MessagingStyle-
     * Nachrichten (EXTRA_MESSAGES, Chat mit Absender) und die Zusatzzeile
     * (EXTRA_SUB_TEXT, z.B. Konto/Kanal). Ergebnis ist mehrzeilig; Dubletten
     * zum Basistext werden vermieden. Fuer die Anzeige/Indizierung, wenn mehr
     * als die eine Textzeile gewuenscht ist. Leer, wenn nichts da ist.
     */
    public static String richText(Notification n) {
        if (n == null || n.extras == null) return "";
        Bundle ex = n.extras;
        StringBuilder sb = new StringBuilder();
        CharSequence text = ex.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence big  = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        CharSequence base = (big != null && (text == null || big.length() > text.length())) ? big : text;
        if (base != null && base.length() > 0) sb.append(base);
        // MessagingStyle: einzelne Nachrichten mit Absender.
        android.os.Parcelable[] msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (msgs != null) {
            for (android.os.Parcelable p : msgs) {
                if (!(p instanceof Bundle)) continue;
                Bundle m = (Bundle) p;
                CharSequence mt = m.getCharSequence("text");
                if (mt == null || mt.length() == 0) continue;
                CharSequence sender = m.getCharSequence("sender");
                String line = (sender != null && sender.length() > 0 ? sender + ": " : "") + mt;
                if (sb.indexOf(line) >= 0) continue;
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
        }
        // InboxStyle: mehrere Zeilen (z.B. Mail-Liste).
        CharSequence[] lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence l : lines) {
                if (l == null || l.length() == 0) continue;
                String s = l.toString();
                if (sb.indexOf(s) >= 0) continue;
                if (sb.length() > 0) sb.append('\n');
                sb.append(s);
            }
        }
        // Zusatzzeile (Konto/Kanal) als Kontext, wenn nicht schon enthalten.
        CharSequence sub = ex.getCharSequence(Notification.EXTRA_SUB_TEXT);
        if (sub != null && sub.length() > 0 && sb.indexOf(sub.toString()) < 0) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(sub);
        }
        return sb.toString();
    }

    /** Das grosse Bild einer Benachrichtigung (BigPictureStyle, z.B. ein Foto in
     *  einer Chat-Nachricht), oder null. Behandelt Bitmap (klassisch) und Icon
     *  (EXTRA_PICTURE_ICON ab Android 12). */
    public static android.graphics.Bitmap bigPicture(Context ctx, Notification n) {
        if (n == null || n.extras == null) return null;
        Bundle ex = n.extras;
        Object p = ex.getParcelable(Notification.EXTRA_PICTURE);
        if (p instanceof android.graphics.Bitmap) return (android.graphics.Bitmap) p;
        Object pIcon = ex.getParcelable("android.pictureIcon"); // EXTRA_PICTURE_ICON
        if (pIcon instanceof android.graphics.drawable.Icon) return iconToBitmap(ctx, (android.graphics.drawable.Icon) pIcon);
        if (p instanceof android.graphics.drawable.Icon) return iconToBitmap(ctx, (android.graphics.drawable.Icon) p);
        return null;
    }

    /** Das grosse Icon/Avatar einer Benachrichtigung (Absenderbild), oder null. */
    public static android.graphics.Bitmap largeIcon(Context ctx, Notification n) {
        if (n == null) return null;
        try {
            android.graphics.drawable.Icon ic = n.getLargeIcon();
            if (ic != null) {
                android.graphics.Bitmap b = iconToBitmap(ctx, ic);
                if (b != null) return b;
            }
        } catch (Throwable ignored) {}
        if (n.extras != null) {
            Object li = n.extras.getParcelable(Notification.EXTRA_LARGE_ICON);
            if (li instanceof android.graphics.Bitmap) return (android.graphics.Bitmap) li;
            if (li instanceof android.graphics.drawable.Icon) return iconToBitmap(ctx, (android.graphics.drawable.Icon) li);
        }
        return null;
    }

    /** Das kleine Statusleisten-Icon einer Benachrichtigung als Bitmap, optional
     *  eingefaerbt (z.B. mit der Benachrichtigungsfarbe {@code n.color}). Diese
     *  kleinen Icons sind monochrome Vorlagen - ohne Einfaerbung erscheinen sie
     *  meist rein weiss. {@code tint == 0} laesst die Originalfarbe. */
    public static android.graphics.Bitmap smallIcon(Context ctx, Notification n, int tint) {
        if (n == null) return null;
        try {
            android.graphics.drawable.Icon ic = n.getSmallIcon();
            if (ic == null) return null;
            android.graphics.drawable.Drawable d = ic.loadDrawable(ctx);
            if (d == null) return null;
            d = d.mutate();
            if (tint != 0) d.setTint(tint);
            int w = Math.max(1, d.getIntrinsicWidth());
            int h = Math.max(1, d.getIntrinsicHeight());
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(bmp);
            d.setBounds(0, 0, w, h);
            d.draw(c);
            return bmp;
        } catch (Throwable t) { return null; }
    }

    /** Wie {@link #smallIcon(Context, Notification, int)} mit der
     *  Benachrichtigungsfarbe als Tint (weiss als Rueckfall). */
    public static android.graphics.Bitmap smallIcon(Context ctx, Notification n) {
        int tint = (n != null && n.color != 0) ? n.color : android.graphics.Color.WHITE;
        return smallIcon(ctx, n, tint);
    }

    private static android.graphics.Bitmap iconToBitmap(Context ctx, android.graphics.drawable.Icon icon) {
        try {
            android.graphics.drawable.Drawable d = icon.loadDrawable(ctx);
            if (d == null) return null;
            if (d instanceof android.graphics.drawable.BitmapDrawable) {
                android.graphics.Bitmap b = ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
                if (b != null) return b;
            }
            int w = Math.max(1, d.getIntrinsicWidth());
            int h = Math.max(1, d.getIntrinsicHeight());
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(bmp);
            d.setBounds(0, 0, w, h);
            d.draw(c);
            return bmp;
        } catch (Throwable t) { return null; }
    }

    /** Eine einzelne Chat-Nachricht aus einer MessagingStyle-Benachrichtigung. */
    public static final class Message {
        public final String sender;   // Absender (kann leer sein = "ich"/unbekannt)
        public final String text;     // Nachrichtentext
        public final long time;       // Zeitstempel (ms), 0 wenn unbekannt
        public Message(String sender, String text, long time) {
            this.sender = sender; this.text = text; this.time = time;
        }
    }

    /**
     * Vollstaendige, strukturierte Auswertung einer Benachrichtigung - ALLES,
     * was sinnvoll auslesbar ist, an EINER Stelle, damit jede App sich nur
     * herausnimmt, was sie braucht. Reine Daten (keine Bitmaps - die holt man
     * bei Bedarf ueber {@link #bigPicture}/{@link #largeIcon}, das spart
     * Speicher und braucht einen Context). Nie null-Strings (leer statt null).
     */
    public static final class Info {
        public String appPackage = "";
        public String category = "";       // Notification.category (z.B. "msg", "email", "call")
        public long when = 0;              // Notification.when (ms)
        public String title = "";          // EXTRA_TITLE
        public String titleBig = "";       // EXTRA_TITLE_BIG (aufgeklappt)
        public String text = "";           // laengster verfuegbarer Fliesstext (text/bigText)
        public String subText = "";        // EXTRA_SUB_TEXT (Konto/Kanal)
        public String infoText = "";       // EXTRA_INFO_TEXT (rechts kleine Info)
        public String summaryText = "";    // EXTRA_SUMMARY_TEXT (BigPicture-Untertitel)
        public String conversationTitle = ""; // EXTRA_CONVERSATION_TITLE (Gruppenname)
        public final java.util.List<String> lines = new java.util.ArrayList<>();     // InboxStyle
        public final java.util.List<Message> messages = new java.util.ArrayList<>(); // MessagingStyle
        public final java.util.List<String> people = new java.util.ArrayList<>();    // EXTRA_PEOPLE(_LIST)
        public boolean groupSummary = false;
        public boolean ongoing = false;    // laufend (Musik, Download, Anruf)
        public int progress = 0, progressMax = 0;
        public boolean progressIndeterminate = false;
        public boolean hasBigPicture = false; // ein grosses Bild ist vorhanden
        public boolean hasLargeIcon = false;  // ein Avatar/grosses Icon ist vorhanden
        public boolean canReply = false, canMarkRead = false, canDelete = false;
        public int actionCount = 0;

        /** Titel + Text so, wie {@link #titleAndText} sie liefert. */
        public String[] titleAndText() { return new String[]{ title, text }; }

        /** Mehrzeiliger Gesamttext: Basistext + Chat-Nachrichten + InboxStyle-
         *  Zeilen + Zusatzzeile, Dubletten vermieden (wie {@link #richText}). */
        public String richText() {
            StringBuilder sb = new StringBuilder();
            if (!text.isEmpty()) sb.append(text);
            for (Message m : messages) {
                if (m.text == null || m.text.isEmpty()) continue;
                String line = (m.sender != null && !m.sender.isEmpty() ? m.sender + ": " : "") + m.text;
                if (sb.indexOf(line) >= 0) continue;
                if (sb.length() > 0) sb.append('\n'); sb.append(line);
            }
            for (String l : lines) {
                if (l == null || l.isEmpty() || sb.indexOf(l) >= 0) continue;
                if (sb.length() > 0) sb.append('\n'); sb.append(l);
            }
            if (!subText.isEmpty() && sb.indexOf(subText) < 0) {
                if (sb.length() > 0) sb.append('\n'); sb.append(subText);
            }
            return sb.toString();
        }
    }

    private static String str(CharSequence cs) { return cs == null ? "" : cs.toString(); }

    /**
     * ALLES aus einer Benachrichtigung als JSON-Text - die strukturiert
     * ausgewerteten Felder (siehe {@link Info}) UND zusaetzlich ein generischer
     * Abzug SAEMTLICHER extras-Schluessel, auch derer, die diese Apps selbst
     * nicht auswerten. Gedacht zum dauerhaften, verlustfreien Vorhalten in der
     * Ablage ("speichern und auswertbar vorhalten"): eine App nimmt sich beim
     * Anzeigen/Durchsuchen die Felder heraus, die sie braucht, ein anderer
     * Wiederverwender findet die uebrigen hier wieder. Bitmaps/Icons werden
     * NICHT eingebettet (nur ihr Vorhandensein/Typ vermerkt) - die holt man bei
     * Bedarf ueber {@link #bigPicture}/{@link #largeIcon}. Nie null; "" bei
     * Fehler.
     */
    public static String toJson(StatusBarNotification sbn, Notification n) {
        try {
            if (n == null && sbn != null) n = sbn.getNotification();
            Info info = describe(sbn, n);
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("appPackage", info.appPackage);
            o.put("category", info.category);
            o.put("when", info.when);
            o.put("title", info.title);
            o.put("titleBig", info.titleBig);
            o.put("text", info.text);
            o.put("subText", info.subText);
            o.put("infoText", info.infoText);
            o.put("summaryText", info.summaryText);
            o.put("conversationTitle", info.conversationTitle);
            org.json.JSONArray lines = new org.json.JSONArray();
            for (String l : info.lines) lines.put(l);
            o.put("lines", lines);
            org.json.JSONArray msgs = new org.json.JSONArray();
            for (Message m : info.messages) {
                org.json.JSONObject mo = new org.json.JSONObject();
                mo.put("sender", m.sender);
                mo.put("text", m.text);
                mo.put("time", m.time);
                msgs.put(mo);
            }
            o.put("messages", msgs);
            org.json.JSONArray people = new org.json.JSONArray();
            for (String p : info.people) people.put(p);
            o.put("people", people);
            o.put("groupSummary", info.groupSummary);
            o.put("ongoing", info.ongoing);
            o.put("progress", info.progress);
            o.put("progressMax", info.progressMax);
            o.put("progressIndeterminate", info.progressIndeterminate);
            o.put("hasBigPicture", info.hasBigPicture);
            o.put("hasLargeIcon", info.hasLargeIcon);
            o.put("canReply", info.canReply);
            o.put("canMarkRead", info.canMarkRead);
            o.put("canDelete", info.canDelete);
            o.put("actionCount", info.actionCount);
            if (n != null) {
                o.put("flags", n.flags);
                o.put("color", n.color);
                o.put("channelId", n.getChannelId());
            }
            if (sbn != null) {
                o.put("key", sbn.getKey());
                o.put("postTime", sbn.getPostTime());
                o.put("id", sbn.getId());
                o.put("tag", sbn.getTag());
            }
            // Saemtliche uebrigen Extras generisch - auch die, die oben nicht
            // strukturiert vorkommen (App-eigene Zusatzfelder etc.).
            if (n != null && n.extras != null) o.put("extras", bundleToJson(n.extras));
            return o.toString();
        } catch (Throwable t) { return ""; }
    }

    /** Alle Schluessel eines Bundle als JSON-Objekt (rekursiv fuer verschachtelte
     *  Bundles). Nicht text-sinnvolle Werte (Bitmaps, Icons, sonstige
     *  Parcelables) werden nur mit ihrem Typ vermerkt. */
    private static org.json.JSONObject bundleToJson(Bundle b) {
        org.json.JSONObject o = new org.json.JSONObject();
        if (b == null) return o;
        for (String key : b.keySet()) {
            try { o.put(key, jsonValue(b.get(key))); } catch (Throwable ignored) {}
        }
        return o;
    }

    private static Object jsonValue(Object v) {
        if (v == null) return org.json.JSONObject.NULL;
        if (v instanceof CharSequence) return v.toString();
        if (v instanceof Boolean || v instanceof Integer || v instanceof Long
                || v instanceof Double || v instanceof Float) return v;
        if (v instanceof Number) return v.toString();
        if (v instanceof CharSequence[]) {
            org.json.JSONArray a = new org.json.JSONArray();
            for (CharSequence c : (CharSequence[]) v) a.put(c == null ? org.json.JSONObject.NULL : c.toString());
            return a;
        }
        if (v instanceof String[]) {
            org.json.JSONArray a = new org.json.JSONArray();
            for (String s : (String[]) v) a.put(s == null ? org.json.JSONObject.NULL : s);
            return a;
        }
        if (v instanceof Bundle) return bundleToJson((Bundle) v);
        if (v instanceof android.os.Parcelable[]) {
            org.json.JSONArray a = new org.json.JSONArray();
            for (android.os.Parcelable p : (android.os.Parcelable[]) v) {
                if (p instanceof Bundle) a.put(bundleToJson((Bundle) p));
                else a.put(p == null ? org.json.JSONObject.NULL : ("[" + p.getClass().getSimpleName() + "]"));
            }
            return a;
        }
        return "[" + v.getClass().getSimpleName() + "]";
    }

    /**
     * Alles aus einer Benachrichtigung strukturiert einsammeln (siehe {@link Info}).
     * {@code sbn} darf null sein - dann fehlen nur die davon abgeleiteten Felder
     * (Paket, when). Bitmaps sind bewusst NICHT enthalten.
     */
    public static Info describe(StatusBarNotification sbn, Notification n) {
        Info info = new Info();
        if (n == null && sbn != null) n = sbn.getNotification();
        if (n == null) return info;
        if (sbn != null) { info.appPackage = sbn.getPackageName() == null ? "" : sbn.getPackageName(); }
        info.category = n.category == null ? "" : n.category;
        info.when = n.when;
        info.ongoing = (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        info.groupSummary = isGroupSummary(n);
        String[] tt = titleAndText(n);
        info.title = tt[0];
        info.text = tt[1];
        Bundle ex = n.extras;
        if (ex != null) {
            info.titleBig = str(ex.getCharSequence(Notification.EXTRA_TITLE_BIG));
            info.subText = str(ex.getCharSequence(Notification.EXTRA_SUB_TEXT));
            info.infoText = str(ex.getCharSequence(Notification.EXTRA_INFO_TEXT));
            info.summaryText = str(ex.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));
            info.conversationTitle = str(ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
            info.progress = ex.getInt(Notification.EXTRA_PROGRESS, 0);
            info.progressMax = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0);
            info.progressIndeterminate = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false);
            CharSequence[] lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null) for (CharSequence l : lines) if (l != null && l.length() > 0) info.lines.add(l.toString());
            android.os.Parcelable[] msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (msgs != null) for (android.os.Parcelable p : msgs) {
                if (!(p instanceof Bundle)) continue;
                Bundle m = (Bundle) p;
                CharSequence mt = m.getCharSequence("text");
                if (mt == null || mt.length() == 0) continue;
                info.messages.add(new Message(str(m.getCharSequence("sender")), mt.toString(), m.getLong("time", 0)));
            }
            String[] people = ex.getStringArray(Notification.EXTRA_PEOPLE);
            if (people != null) for (String p : people) if (p != null && !p.isEmpty()) info.people.add(p);
            info.hasBigPicture = ex.getParcelable(Notification.EXTRA_PICTURE) != null
                    || ex.getParcelable("android.pictureIcon") != null;
        }
        try { info.hasLargeIcon = n.getLargeIcon() != null; } catch (Throwable ignored) {}
        info.canReply = findAnyReplyAction(n) != null;
        info.canMarkRead = findMarkReadAction(n) != null;
        info.canDelete = findDeleteAction(n) != null;
        info.actionCount = n.actions == null ? 0 : n.actions.length;
        return info;
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
