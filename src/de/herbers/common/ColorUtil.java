package de.herbers.common;

import android.graphics.Color;

/**
 * Stabile, gut unterscheidbare Farbe je Schluessel (z.B. Paketname oder
 * Dateiendung) - aus dem Hashwert eine Farbtonwahl, feste Saettigung/Helligkeit.
 * Derselbe Schluessel ergibt immer dieselbe Farbe (z.B. fuer farbige Balken je
 * Quell-App). Zuvor in EdgeTab und Sucher identisch dupliziert.
 */
public final class ColorUtil {

    private ColorUtil() {}

    public static int colorFor(String key) {
        int hue = Math.floorMod(key == null ? 0 : key.hashCode(), 360);
        return Color.HSVToColor(new float[]{hue, 0.5f, 0.85f});
    }
}
