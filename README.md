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
| `Notifications` | Shared notification-capture core. **Text:** `titleAndText` (prefers big text), `richText` (folds in MessagingStyle chat lines, InboxStyle multi-line and the sub-text). **Structured:** `describe(sbn, n) → Info` reads *everything* useful at once (category, when, all title/text variants, sub/info/summary/conversation text, InboxStyle lines, MessagingStyle messages with sender+time, people, progress, ongoing, `canReply`/`canMarkRead`/`canDelete`, big-picture/large-icon presence). **Lossless:** `toJson(sbn, n)` serialises the `Info` fields **plus a generic dump of every remaining `extras` key** – for storing a notification verbatim so any reuser finds fields this app itself ignores (bitmaps not embedded, only their presence/type noted). **Images:** `bigPicture`, `largeIcon`, `smallIcon` (optionally tinted) → `Bitmap`. **Filters/utility:** group-summary/blank/"real notification" tests, `signature` (content identity), `appLabel`. **Actions:** reply (free-text `RemoteInput` and label/semantic-only), delete, **mark as read** incl. Wearable actions, plus `buildReplyFillIn`. Pure static helpers on `Notification`/`StatusBarNotification`, no app coupling. |
| `Apps` | `launchable(ctx)` – all launcher apps (excluding your own) as `{package, label}`, sorted by label. Basis for app-picker lists (e.g. "notification sources"); each app appends its own already-observed senders. |
| `DiagLog` | Tiny persistent on-device diagnostic log (`getFilesDir/diag.log`, capped, mirrored to logcat with a per-app tag via `setTag`). `log/read/clear`. Viewable/clearable in-app without a cable. |
| `Diagnostics` | `stackOf(Thread)` / `stackOf(Throwable)` to capture where something hangs/crashed, and `installCrashLogger(ctx)` to record uncaught crashes into `DiagLog`. The app-specific watchdog around a long-running worker stays in the app. |

Each app keeps its own `NotificationListenerService` (what it does with a
notification differs), but now shares the field extraction, filters and action
detection through `Notifications`. Planned next: more small helpers.

## Usage

```java
// Backup / restore (e.g. behind a "Back up" / "Restore" button)
String text = SettingsBackup.export(prefs, "MyApp-Backup 1");
boolean ok  = SettingsBackup.importInto(prefs, "MyApp-Backup 1", text);

int color = ColorUtil.colorFor(packageName);
```

Inside your `NotificationListenerService.onNotificationPosted(sbn)`:

```java
Notification n = sbn.getNotification();
if (Notifications.isGroupSummary(n)) return;          // skip "3 new messages"

String[] tt = Notifications.titleAndText(n);          // {title, text}
String searchable = Notifications.richText(n);        // chat lines + inbox lines + sub-text

Notifications.Info info = Notifications.describe(sbn, n);   // everything, structured
if (info.canReply) { /* offer a reply field */ }

String json = Notifications.toJson(sbn, n);           // everything, lossless (store this)
// ...persist `json` in your own table; later read any field back, even ones
// this app never displays. Bitmaps aren't embedded:
android.graphics.Bitmap avatar = Notifications.largeIcon(ctx, n);
android.graphics.Bitmap photo  = Notifications.bigPicture(ctx, n);
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
