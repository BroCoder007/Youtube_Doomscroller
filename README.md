# Youtube Doomscroller

A Kotlin Android app that auto-scrolls YouTube Shorts/Reels hands-free and supports voice commands like **“like”** and **“comment”**.

## Features

- Hands-free auto-scrolling between shorts/reels
- Configurable wait time before moving to the next video
- Voice commands:
  - `like` → performs like action
  - `comment` → opens the comment box
- Built with Kotlin

## Tech Stack

- **Language:** Kotlin (100%)
- **Platform:** Android

## Getting Started

### Prerequisites

- Android Studio (latest stable recommended)
- Android SDK configured in Android Studio
- A physical Android device or emulator

### Clone the Repository

```bash
git clone https://github.com/BroCoder007/Youtube_Doomscroller.git
cd Youtube_Doomscroller
```

### Open and Run

1. Open the project in Android Studio.
2. Let Gradle sync complete.
3. Run the app on an emulator or connected Android device.

## Usage

1. Launch the app.
2. Set your preferred waiting time for auto-scroll.
3. Start doomscrolling mode.
4. Use voice commands:
   - Say **“like”** to like the current short/reel.
   - Say **“comment”** to open comments.

## Permissions

Depending on implementation, the app may require:

- **Microphone** for voice commands
- **Accessibility** permissions for automated interaction/scrolling

> If prompted, grant required permissions in device settings for full functionality.

## Project Structure

A typical Android/Kotlin project layout:

```text
Youtube_Doomscroller/
├─ app/
│  ├─ src/
│  │  ├─ main/
│  │  │  ├─ java/...        # Kotlin source files
│  │  │  ├─ res/            # Layouts, drawables, values
│  │  │  └─ AndroidManifest.xml
│  ├─ build.gradle
├─ gradle/
├─ build.gradle
└─ settings.gradle
```

## Notes

- Voice recognition accuracy can vary by device and environment noise.
- Ensure app permissions remain enabled after updates.

## Roadmap (Suggested)

- Add additional voice commands (pause, resume, next)
- Improve on-device speech recognition fallback
- Add in-app onboarding for permissions setup
- Add unit/UI tests

## Contributing

Contributions are welcome.

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Open a pull request

## License

No license file is currently present in this repository.
If you want open-source usage, add a `LICENSE` file (e.g., MIT).
