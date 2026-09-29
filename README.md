# herbers-android-common

Kleine gemeinsame Bausteine der Apps **EdgeTab**, **Sucher**, **ActiveFrames**
und **BBMePing** – herausgelöst, damit mehrfach genutzte Funktionen in allen
Programmen gleich aufgebaut sind (weniger Pflege). Paket `de.herbers.common`.

## Enthalten

| Klasse | Zweck |
|---|---|
| `SettingsBackup` | Textbasierte Sicherung/Wiederherstellung einer kompletten `SharedPreferences`-Datei (`export`/`importInto`). Vereinheitlicht die zuvor je App kopierte Logik und **behebt Suchers fehlerhaften Restore** (dessen Import erwartete ein anderes Feld-Layout als der Export erzeugte). |
| `ColorUtil` | `colorFor(key)` – stabile, unterscheidbare Farbe je Schlüssel (Paketname/Dateiendung), z. B. für farbige Balken je Quell-App. |

Geplant (nächste Schritte): gemeinsamer **Benachrichtigungs-Kern**
(NotificationListener-Basis, Feld-Extraktion, Quellen-Auswahl inkl. „als gelesen
markieren") und weitere Kleinhelfer.

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
