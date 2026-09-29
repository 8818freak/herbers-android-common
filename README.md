# herbers-android-common

*(English · [Deutsch](README.de.md))*

Small shared building blocks for the Android apps **EdgeTab**, **Sucher**,
**ActiveFrames** and **BBMePing** – factored out so that functionality used by
more than one app shares a single, maintained implementation. Package
`de.herbers.common`. **Zero third-party dependencies** (only `android.jar`).

## What's inside

| Class | Purpose |
|---|---|
| `SettingsBackup` | Text-based backup/restore of a whole `SharedPreferences` file (`export` / `importInto`). Unifies the logic previously copied into each app. |
| `ColorUtil` | `colorFor(key)` – a stable, distinct colour per key (package name / file extension), e.g. a coloured bar per source app. |

Planned next: a shared **notification core** (NotificationListener base, field
extraction, source selection incl. "mark as read") and more small helpers.

## Usage

```java
// Backup / restore (e.g. behind a "Back up" / "Restore" button)
String text = SettingsBackup.export(prefs, "MyApp-Backup 1");
boolean ok  = SettingsBackup.importInto(prefs, "MyApp-Backup 1", text);

int color = ColorUtil.colorFor(packageName);
```

## Building it in

Designed as a **source module**: apps add it as a Git submodule and compile
`src/` alongside their own sources (raw Android SDK build, no Gradle).

```
git submodule add https://github.com/8818freak/herbers-android-common common
# Build: compile the app's src/ plus common/src/.
```

Only `android.jar` is required. Tested on Android 10+ (API 29); Java 8 language
features.

## License

**GNU Lesser General Public License v3.0 (or later)** – see `LICENSE`. The LGPLv3
builds on the [GPLv3](https://www.gnu.org/licenses/gpl-3.0.html). This means the
library can be linked from non-GPL apps too, while changes to the library itself
stay copyleft. Copyright © 2026 Mathias Herbers.
