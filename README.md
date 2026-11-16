# Doomscroller for Android

Hands-free controls for YouTube Shorts, Instagram Reels and TikTok. Version 1.1 adds an explicit session switch, smart completion detection, safer voice commands, a session limit, and live status messages.

## Use it

1. Install the debug APK for testing, or build the app in Android Studio.
2. Open Doomscroller. Read the accessibility disclosure, open Accessibility settings and enable Doomscroller.
3. Keep **Smart mode** enabled to wait for observed completion. Choose Timer mode only if you want a fixed interval.
4. Optionally enable **Listen for commands**, grant microphone access, and keep the default wake phrase enabled.
5. Tap **Start auto-scroll**, then open a supported short-video viewer.
6. Say **“scroller pause”** to pause, or **“scroller resume”** to continue. A notification provides Pause and Stop session & mic actions when voice is active.

Starting a session does not enable the microphone. Voice must be explicitly enabled from the visible app. Pausing auto-scroll keeps an already enabled microphone listening so you can resume by voice. Disable Listen for commands or use Stop session & mic to release it.

## Commands

| Say after “scroller” | Effect |
| --- | --- |
| pause / stop | Pause automatic scrolling |
| resume / start | Resume automatic scrolling |
| next / skip | Pause and move forward once |
| previous / go back | Pause and move backward |
| like | Pause, then click an explicitly unliked Like control |
| comment / open comments | Pause and open comments; never type or submit |
| smart mode | Pause and select completion detection |
| timer mode | Pause and select interval scrolling |

Recognition accepts a complete command, rather than matching words inside video dialogue. Only final results trigger commands. The optional “scroller” prefix is required by default. This is a recognition grammar, not a dedicated always-on wake-word engine. Headphones help avoid commands from video audio.

## How smart mode works

Android cannot directly read another app’s video player. Doomscroller inspects visible playback controls exposed through Android accessibility. It accepts playback ranges and explicit elapsed/total timestamps from playback controls, watches observed progress, and advances at an observed end or a near-end-to-start loop. It does **not** guess video duration, scan captions for times, or switch to a fixed timeout.

If an app version hides playback progress, Smart mode reports that progress is unavailable and waits. Use “next” manually or deliberately select Timer mode. Known English player IDs/labels are used; app updates, localization, ads, live streams, slideshows, comments and overlays can prevent detection. TikTok package variants are supported where their playback controls are accessible. This is best-effort native-app integration, not a guarantee for every app version.

Native completion is inferred from accessible progress. Abrupt jumps are rejected, but manual seeking very near the end can still resemble a natural loop. Pause automation before scrubbing playback.

Automation is restricted to recognized short-video views in supported packages. It pauses on unsupported windows, device lock and comments/editing views. Timer mode resets on context changes; it cannot prove a clip is playing if the native app exposes no playback state. Session limits are optional (Off / 15 / 30 / 60 minutes).

## Privacy and permissions

- **Accessibility:** reads visible controls/progress and performs user-requested swipes/clicks in supported views. No screen capture or accessibility telemetry is uploaded by this app.
- **Microphone:** only for an explicitly enabled voice session, with a foreground notification. The recognizer prefers Android’s on-device service when available; otherwise the selected system recognition provider may process audio online. Continuous listening can use battery/data and is limited by the device’s speech provider.
- **Notifications:** requested on Android 13+ so microphone controls remain visible. Android may show a foreground-service notice even if notification permission is declined.
- Commands, settings and status stay in private local preferences. Transcripts are not logged or saved by Doomscroller.

## Build and test

Use JDK 21, Android SDK platform **36.1**, and the bundled Gradle wrapper (9.4.1). Set `ANDROID_HOME` or an ignored `local.properties` file containing `sdk.dir=...`.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. It is a test build, not a store-signed release. CI builds, lints and runs unit tests and uploads the debug APK.

Unit tests cover command boundaries, wake phrase handling, accessible timestamp parsing, completion/loop evidence, missing/stale progress and duplicate completion suppression. Physical-device testing remains necessary for current YouTube, Instagram and TikTok accessibility trees and the installed speech provider.

## Desktop browser extension

The companion project is maintained separately as [Doomscroller-Web](https://github.com/BroCoder007/Doomscroller-Web). It reads actual HTML video playback on supported desktop routes and provides Chrome/Edge controls and opt-in voice in a side panel.

## Technical references

- [Android accessibility view-ID reporting](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_REPORT_VIEW_IDS)
- [Android SpeechRecognizer lifecycle and continuous-use limits](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [Microphone foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone)

No license was present in the original repository. Choose a license before distributing this as an open-source package.
