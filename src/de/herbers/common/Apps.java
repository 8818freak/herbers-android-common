package de.herbers.common;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Gemeinsame App-Aufzaehlung. {@link #launchable} liefert alle Apps mit
 * Startsymbol (ACTION_MAIN + CATEGORY_LAUNCHER), ohne die eigene, als
 * {Paketname, Anzeigename} und nach Anzeigename sortiert. Basis fuer
 * App-Auswahllisten (z.B. "Benachrichtigungsquellen"); zuvor in EdgeTab und
 * Sucher nahezu identisch dupliziert. Bereits beobachtete Absender ohne
 * Startsymbol (aus dem jeweiligen Store) haengt jede App selbst an.
 */
public final class Apps {

    private Apps() {}

    /** {pkg, label} je startbarer App (ohne die eigene), nach label sortiert. */
    public static List<String[]> launchable(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        // Sortierung ueber TreeMap-Schluessel (kleingeschriebenes Label + Zaehler),
        // nicht ueber einen Lambda-Comparator - Letzterer war ein d8-Fallstrick.
        TreeMap<String, String[]> sorted = new TreeMap<>();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        int idx = 0;
        if (apps != null) for (ResolveInfo ri : apps) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName())) continue;
            String label;
            try { label = ri.loadLabel(pm).toString(); } catch (Throwable t) { label = pkg; }
            sorted.put(label.toLowerCase(Locale.ROOT) + "" + (idx++), new String[]{pkg, label});
        }
        return new ArrayList<>(sorted.values());
    }
}
