package de.herbers.common;

import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Textbasierte Sicherung/Wiederherstellung einer kompletten
 * SharedPreferences-Datei - zum Anzeigen/Kopieren oder Ablegen als Datei.
 *
 * Format je Zeile: ein Typkuerzel + Schluessel in EINEM Feld, danach (per
 * {@code } getrennt) der Wert; Zeichenketten und String-Mengen sind
 * Base64-kodiert (damit Trenn-/Zeilenzeichen im Wert nichts kaputtmachen):
 * <pre>
 *   &lt;header&gt;
 *   s&lt;key&gt;&lt;base64&gt;   // String
 *   b&lt;key&gt;&lt;true|false&gt;
 *   i&lt;key&gt;&lt;int&gt;   l&lt;key&gt;&lt;long&gt;   f&lt;key&gt;&lt;float&gt;
 *   x&lt;key&gt;&lt;base64 der -verbundenen Mengen-Elemente&gt;  // Set&lt;String&gt;
 * </pre>
 *
 * Vereinheitlicht die zuvor in EdgeTab/ActiveFrames/Sucher je App kopierte
 * Logik. Die frueheren Kopien waren im Export identisch; nur Suchers Import war
 * fehlerhaft (erwartete drei durch {@code } getrennte Felder, obwohl der
 * Export zwei erzeugt) - er stellte darum nichts wieder her. Diese
 * gemeinsame, korrekte Fassung behebt das.
 */
public final class SettingsBackup {

    private static final char SEP = '';

    private SettingsBackup() {}

    /** Gesamten Preferences-Stand als Text mit vorangestellter Kennzeile. */
    public static String export(SharedPreferences p, String header) {
        StringBuilder sb = new StringBuilder(header).append('\n');
        for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
            String key = e.getKey();
            Object v = e.getValue();
            if (v instanceof String) {
                sb.append('s').append(key).append(SEP).append(b64((String) v)).append('\n');
            } else if (v instanceof Boolean) {
                sb.append('b').append(key).append(SEP).append(v).append('\n');
            } else if (v instanceof Integer) {
                sb.append('i').append(key).append(SEP).append(v).append('\n');
            } else if (v instanceof Long) {
                sb.append('l').append(key).append(SEP).append(v).append('\n');
            } else if (v instanceof Float) {
                sb.append('f').append(key).append(SEP).append(v).append('\n');
            } else if (v instanceof Set) {
                StringBuilder joined = new StringBuilder();
                for (Object s : (Set<?>) v) joined.append(String.valueOf(s)).append(SEP);
                sb.append('x').append(key).append(SEP).append(b64(joined.toString())).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Ersetzt den GESAMTEN Preferences-Stand durch die Sicherung. Liefert
     * false bei erkennbar falscher Kennzeile/beschaedigtem Format, ohne etwas
     * zu aendern (soweit vor dem apply() erkennbar).
     */
    public static boolean importInto(SharedPreferences p, String header, String text) {
        if (text == null || !text.startsWith(header)) return false;
        SharedPreferences.Editor ed = p.edit().clear();
        try {
            String sep = String.valueOf(SEP);
            for (String line : text.split("\n", -1)) {
                if (line.isEmpty()) continue;
                String[] f = line.split(sep, -1);
                if (f.length < 2 || f[0].isEmpty()) continue;
                String type = f[0].substring(0, 1);
                String key = f[0].substring(1);
                String val = f[1];
                switch (type) {
                    case "s": ed.putString(key, unb64(val)); break;
                    case "b": ed.putBoolean(key, Boolean.parseBoolean(val)); break;
                    case "i": ed.putInt(key, Integer.parseInt(val)); break;
                    case "l": ed.putLong(key, Long.parseLong(val)); break;
                    case "f": ed.putFloat(key, Float.parseFloat(val)); break;
                    case "x":
                        Set<String> set = new HashSet<>();
                        for (String part : unb64(val).split(sep, -1)) if (!part.isEmpty()) set.add(part);
                        ed.putStringSet(key, set);
                        break;
                    default: break;
                }
            }
        } catch (Exception e) {
            return false;
        }
        ed.apply();
        return true;
    }

    private static String b64(String s) {
        return Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private static String unb64(String s) {
        return new String(Base64.decode(s, Base64.NO_WRAP), StandardCharsets.UTF_8);
    }
}
