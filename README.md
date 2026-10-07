# DM Only

Instagram with only the messages, for Android. The app opens straight to your inbox. The home feed, Reels tab and Explore never load. A reel someone sends you in a DM still plays, but you can't swipe past it into the next one.

It is Instagram's own website in an Android WebView with a script injected at document start. There is no backend: you log in on Instagram's login page, and your password and messages never leave Instagram.

Inspired by [Konvo](https://github.com/matthewcycmb/konvo), which does the same for iPhone.

## What's blocked

| Path | Result |
| --- | --- |
| `/` (home feed) | sent to `/direct/inbox/` |
| `/reels/` (Reels tab) | sent to the inbox |
| `/explore/...` | sent to the inbox |
| `/<user>/reels/`, `/tagged/`, `/saved/` | sent to the inbox |
| `/reel/<id>/` (a shared reel) | plays, but is locked to that one reel |

Links to blocked pages are hidden and taps on them do nothing. Profiles, single posts, stories and notifications still work.

**Reel lock.** When a reel opens, its id is remembered. Swipe, wheel and arrow-key scrolling on the scroll container that holds the video is cancelled, and any scroll that slips through is snapped back. If the URL changes to a different reel anyway, the app reloads the one that was shared. Comments still scroll, because their container has no video in it.

## Notifications

Android WebView does not support Web Push, so Instagram's own notifications can't reach the app. DM Only builds its own in two ways:

1. **While the app is alive.** `cage.js` reads the unread count from the page title (`(3) Instagram`) and posts it to the app through a web message listener limited to `https://*.instagram.com`.
2. **While the app is closed.** `UnreadWorker` runs about every 15 minutes (Android's minimum) and calls Instagram's web endpoint `/api/v1/direct_v2/get_badge_count/` with the session cookies the WebView saved.

Both feed `Notifier`, which alerts only when the count goes up and you're not in the app. Expect delays of up to 15 minutes when the app is closed, longer in Doze. Some phones (Xiaomi, Samsung, etc.) need battery use set to "Unrestricted" for background checks to run.

## Download

**[Download dm-only.apk](https://github.com/safwanpp/dm-only/releases/latest/download/dm-only.apk)** (latest release, Android 8.0+). All versions are on the [Releases](https://github.com/safwanpp/dm-only/releases) page.

Open the APK on your phone and allow "install unknown apps" when asked. Play Protect may warn because the APK is signed with a debug key.

## Build from source

Build the APK (needs the Android SDK and JDK 17):

```sh
./gradlew assembleRelease
```

The APK is at `app/build/outputs/apk/release/app-release.apk`. Copy it to your phone and open it (allow "install unknown apps"), or over USB:

```sh
adb install app/build/outputs/apk/release/app-release.apk
```

Requires Android 8.0 (API 26) or later. The release build is signed with the debug key, which is fine for sideloading but not for the Play Store.

## Project layout

| File | Role |
| --- | --- |
| `app/src/main/assets/cage.js` | The blocking script: path rules, reel lock, hidden links, unread reporting |
| `app/src/main/java/com/safwan/dmonly/MainActivity.java` | WebView setup, document-start injection, file uploads, link routing |
| `app/src/main/java/com/safwan/dmonly/Notifier.java` | Unread count to a single Android notification |
| `app/src/main/java/com/safwan/dmonly/UnreadWorker.java` | Background badge-count check |

Pages on `instagram.com`, `facebook.com`, `meta.com`, `fbcdn.net` and `cdninstagram.com` stay in the app so login and two-factor work. Other links open in your browser.

## When Instagram changes

Instagram changes its markup and routes without notice. If a feed surface comes back, add its path to `BLOCK` in `cage.js`. If a link needs hiding, add a selector to `css`. Then rebuild and reinstall.

## Caveats

- The reel lock assumes the reel feed is a scroll container holding `<video>` elements. A redesign can break this; the URL check is the fallback.
- The badge-count endpoint is undocumented and may change. Calling it on a schedule from your session is against Instagram's terms; the risk is low but real.
- The page-title count may include activity notifications, not only DMs.
- Not affiliated with Instagram or Meta.
