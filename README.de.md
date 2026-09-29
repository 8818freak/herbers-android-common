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
| `Notifications` | Gemeinsamer Kern fürs Mitschneiden von Benachrichtigungen: Titel/Text-Extraktion (bevorzugt BigText), Filter (Gruppen-Summary/leer), App-Label, „echte Benachrichtigung"-Test, Inhalts-Signatur und Aktions-Finder (Antworten, Löschen, **als gelesen**) inkl. Wearable-Aktionen. Reine statische Helfer auf `Notification`/`StatusBarNotification`, ohne App-Kopplung. |
| `Apps` | `launchable(ctx)` – alle Apps mit Startsymbol (ohne die eigene) als `{Paketname, Anzeigename}`, nach Anzeigename sortiert. Basis für App-Auswahllisten (z. B. „Benachrichtigungsquellen"); bereits beobachtete Absender hängt jede App selbst an. |

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
