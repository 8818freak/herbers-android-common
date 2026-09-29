package de.herbers.common;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Nimmt den "Ignorieren"-Tipp einer {@link PermReminder}-Erinnerung entgegen:
 * merkt sich, dass diese Berechtigung bewusst nicht erteilt ist (dann keine
 * weitere Erinnerung, bis sie wieder erteilt und erneut verloren wird) und
 * zieht die Benachrichtigung zurueck.
 *
 * <p>Jede App muss diesen Receiver im Manifest deklarieren:
 * {@code <receiver android:name="de.herbers.common.PermReminderReceiver" android:exported="false"/>}
 */
public class PermReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ctx == null || intent == null) return;
        String key = intent.getStringExtra(PermReminder.EXTRA_KEY);
        int id = intent.getIntExtra(PermReminder.EXTRA_NOTIF_ID, -1);
        if (key != null) {
            SharedPreferences p = ctx.getSharedPreferences(PermReminder.PREFS, Context.MODE_PRIVATE);
            p.edit().putBoolean(key + "_ignored", true).apply();
        }
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && id >= 0) { try { nm.cancel(id); } catch (Throwable ignored) {} }
    }
}
