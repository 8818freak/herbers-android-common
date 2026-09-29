# herbers-android-common

*([English](README.md) · Deutsch)*

Kleine gemeinsame Bausteine der Apps **EdgeTab**, **Sucher**, **ActiveFrames**
und **BBMePing** – herausgelöst, damit mehrfach genutzte Funktionen in allen
Programmen gleich aufgebaut sind (weniger Pflege). Paket `de.herbers.common`.

## Enthalten

| Klasse | Zweck |
|---|---|
| `SettingsBackup` | Textbasierte Sicherung/Wiederherstellung einer kompletten `SharedPreferences`-Datei (`export`/`importInto`). Vereinheitlicht die zuvor je App kopierte Logik und **behebt Suchers fehlerhaften Restore** (dessen Import erwartete ein anderes Feld-Layout als der Export erzeugte). |
| `ColorUtil` | `colorFor(key)` – stabile, unterscheidbare Farbe je Schlüssel (Paketname/Dateiendung), z. B. für farbige Balken je Quell-App. |
| `Notifications` | Gemeinsamer Kern fürs Mitschneiden von Benachrichtigungen. **Text:** `titleAndText` (bevorzugt BigText), `richText` (bezieht MessagingStyle-Chatzeilen, InboxStyle-Mehrzeiler und die Zusatzzeile ein). **Strukturiert:** `describe(sbn, n) → Info` liest *alles* Sinnvolle auf einmal (Kategorie, Zeit, alle Titel-/Text-Varianten, Zusatz-/Info-/Summary-/Gesprächstitel, InboxStyle-Zeilen, MessagingStyle-Nachrichten mit Absender+Zeit, Personen, Fortschritt, „läuft", `canReply`/`canMarkRead`/`canDelete`, Vorhandensein von Großbild/Großicon). **Verlustfrei:** `toJson(sbn, n)` serialisiert die `Info`-Felder **plus einen generischen Abzug SÄMTLICHER übriger `extras`-Schlüssel** – um eine Benachrichtigung wortgetreu abzulegen, sodass ein Wiederverwender auch Felder wiederfindet, die diese App selbst ignoriert (Bitmaps nicht eingebettet, nur Vorhandensein/Typ vermerkt). **Bilder:** `bigPicture`, `largeIcon`, `smallIcon` (optional eingefärbt) → `Bitmap`. **Filter/Helfer:** Gruppen-Summary-/Leer-/„echte Benachrichtigung"-Tests, `signature` (Inhalts-Identität), `appLabel`. **Aktionen:** Antworten (Freitext-`RemoteInput` sowie nur-semantisch/beschriftet), Löschen, **als gelesen** inkl. Wearable-Aktionen, dazu `buildReplyFillIn`. **Inhaltsschutz:** `isConversational` (Chat/Mail?) und `looksLikeReplyConfirmation` (ein bloßer „gesendet"/„geantwortet"/leerer Neu-Post) lassen eine Ablage eine echte Nachricht vor dem Überschreiben durch einen Bestätigungs-Neupost der App bewahren – echte neue Nachrichten kommen weiter durch. Reine statische Helfer auf `Notification`/`StatusBarNotification`, ohne App-Kopplung. |
| `NotificationActionCache` | Hält die „Drähte" (contentIntent, Löschen-Aktion, Antworten-Aktion, Als-gelesen-Aktion) von Benachrichtigungen je Schlüssel fest, damit eine App noch antworten/als-gelesen-markieren/löschen kann, **nachdem die Benachrichtigung aus der Statusleiste verschwand**. `remember(sbn)` beim Eintreffen; `replyAction/deleteAction/markReadAction/contentIntent(key)` später. Nur im RAM (ein `PendingIntent` ist ein lebendes IPC-Token, nicht speicherbar): ein Neustart oder das Ende dieses Prozesses leert die Ablage, und ein Draht kann ungültig werden, wenn die Quell-App aktualisiert/beendet wird – daher zuerst die lebende Benachrichtigung versuchen, dann den Cache, dann die App öffnen. |
| `Apps` | `launchable(ctx)` – alle Apps mit Startsymbol (ohne die eigene) als `{Paketname, Anzeigename}`, nach Anzeigename sortiert. Basis für App-Auswahllisten (z. B. „Benachrichtigungsquellen"); bereits beobachtete Absender hängt jede App selbst an. |
| `DiagLog` | Kleines, dauerhaftes Diagnose-Protokoll im App-Speicher (`getFilesDir/diag.log`, gedeckelt, gespiegelt nach logcat mit per-App-Tag via `setTag`). `log/read/clear`. In der App einsehbar/löschbar, ohne Kabel. |
| `Diagnostics` | `stackOf(Thread)` / `stackOf(Throwable)` hält fest, wo etwas hängt/abstürzt, und `installCrashLogger(ctx)` schreibt unbehandelte Abstürze ins `DiagLog`. Der app-spezifische Watchdog um einen langlaufenden Arbeiter bleibt in der App. |

Jede App behält ihren eigenen `NotificationListenerService` (was sie mit einer
Benachrichtigung tut, unterscheidet sich), teilt sich aber nun Feld-Extraktion,
Filter und Aktions-Erkennung über `Notifications`. Geplant: weitere Kleinhelfer.

## Nutzung

```java
// Sicherung (z. B. hinter einem "Sichern"/"Wiederherstellen"-Knopf)
String text = SettingsBackup.export(prefs, "MeineApp-Backup 1");
boolean ok  = SettingsBackup.importInto(prefs, "MeineApp-Backup 1", text);

int color = ColorUtil.colorFor(packageName);
```

In deinem `NotificationListenerService.onNotificationPosted(sbn)`:

```java
Notification n = sbn.getNotification();
if (Notifications.isGroupSummary(n)) return;          // „3 neue Nachrichten" überspringen

String[] tt = Notifications.titleAndText(n);          // {Titel, Text}
String durchsuchbar = Notifications.richText(n);      // Chatzeilen + Inbox-Zeilen + Zusatzzeile

Notifications.Info info = Notifications.describe(sbn, n);   // alles, strukturiert
if (info.canReply) { /* Antwortfeld anbieten */ }

String json = Notifications.toJson(sbn, n);           // alles, verlustfrei (das ablegen)
// ...`json` in eigener Tabelle speichern; später jedes Feld wieder auslesen,
// auch solche, die diese App nie anzeigt. Bitmaps sind nicht eingebettet:
android.graphics.Bitmap avatar = Notifications.largeIcon(ctx, n);
android.graphics.Bitmap foto   = Notifications.bigPicture(ctx, n);
```

## Einbinden

Als **Quell-Modul** gedacht: die Apps binden es als Git-Submodul ein und
kompilieren `src/` mit (Raw-SDK-Build ohne Gradle).

```
git submodule add https://github.com/8818freak/herbers-android-common common
# Build: src/ der App plus common/src/ mitkompilieren.
```

Nur `android.jar` nötig (keine Drittanbieter-Abhängigkeiten). Getestet auf
Android 10+ (API 29); Java-8-Sprachfeatures.

## Lizenz

**GNU Lesser General Public License v3.0 (oder später)** – siehe `LICENSE`. Die
LGPLv3 baut auf der [GPLv3](https://www.gnu.org/licenses/gpl-3.0.html) auf.
Einbindbar auch aus nicht-GPL-Apps; Änderungen an der Bibliothek selbst bleiben
copyleft. Copyright © 2026 Mathias Herbers.
