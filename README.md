# 🧠 Shutup & Study

🚀 **[Open Web Application](https://abhijitsarode.github.io/Shutup-Study/)**

A collaborative, distraction-free Pomodoro study platform. Host synchronized group study rooms with real-time Firebase sync, or study independently with a private offline timer. Available on the **Web**, as a native **macOS Utility App**, and as a native **Android Mobile App**.

---

## ✨ Features

### 💻 Unified Features (Web & Desktop)
- **🎨 Minimalist Neumorphic UI**: Embossed cards, debossed inputs, and clean layouts that adapt to system Light/Dark appearance.
- **🔄 Real-Time Synchronized Timers**: High-accuracy client-side countdowns synced to Firestore server timestamps, avoiding write rate limits while keeping everyone perfectly aligned.
- **⚡ Hands-Free Auto-Transitions**: Timer phases automatically advance (Study ➔ Short Break ➔ Long Break) to minimize user interactions so you can stay in the zone.
- **🎵 Phase-Specific Synthesizer Chimes**: Dynamic audio chimes generated using native audio APIs (ascending melody for Focus, relaxing tones for Break, and an uplifting arpeggio for Long Break).
- **🏆 Set Completion Flow**: A congratulatory overlay modal triggers once a full Pomodoro cycle is finished. The leader can restart the cycle or discard/close the room.

### ⏱️ Solo Study Mode (100% Offline)
- A private, distraction-free Pomodoro timer designed for independent study.
- Bypasses all network polling, Firestore connection heartbeats, and database updates.
- No accounts, room codes, username configuration, or internet connection required.
- Full local template settings controls (Focus time, Break times, and Intervals).

---

## 🍏 Native macOS Desktop App (`macos/`)

A high-performance native macOS desktop application written in **Swift** and built with **SwiftUI** for macOS Ventura (13.0) and later.

### Key Desktop Features
- **Distraction-Free Workspace**: Widescreen layout (`900x700`) centered vertically, ignoring safe areas, matching the clean browser dashboard design.
- **Seamless Modal Overlays**: Floating template editor and cycle complete views using custom overlays with dark backdrop dimming, keeping the UI clean and unified.
- **System Menu Bar Integration**: Displays the active countdown timer and session phase (e.g. `25:00 (Focus)`) directly in the macOS menu bar.
- **AVFoundation Audio Synthesizer**: Native audio engine generates stereophonic sine-wave chimes on active phase transitions.
- **Auto-Quitting Frame Cache**: Uses a custom `NSApplicationDelegate` that disables macOS frame caching (`NSQuitAlwaysKeepsWindows`) to guarantee the app always launches centered at the correct widescreen dimensions.

### Build & Install macOS App
To build the native macOS app and install it locally:

1. **Build and package the app**:
   ```bash
   ./build_macos.sh
   ```
   *This compiles the Swift sources and builds the application bundle (`ShutupStudy.app`) in the project root.*

2. **Install to your local Applications folder**:
   ```bash
   rm -rf /Applications/ShutupStudy.app && cp -r ShutupStudy.app /Applications/ShutupStudy.app
   ```
   *You can now search for "Shutup & Study" in Spotlight or pin it to your Dock.*

---

## 🤖 Native Android Mobile App (`android/`)

A native Android mobile application written in **Kotlin** and built with **Jetpack Compose** and **Kotlin Coroutines**.

### Key Mobile Features
- **Monochrome Neumorphic UI**: Recreates the light/dark Neumorphic aesthetic using Compose canvas rendering and customized drawing offsets.
- **High-Precision Background Service**: Utilizes a foreground Android Service (type `specialUse`) to manage countdown progress. Stays alive when minimized, showing lock screen media controls (Play, Pause, Reset, Skip) and status bar notifications.
- **Nothing Phone Glyph Interface**: Integrates matrix LED ticks directly with physical Glyph progress bars on Nothing Phone (2) using customized native JNI matrix SDK bridges.
- **Home Screen Widget**: A custom Android app widget that displays live room status and timer progress on the device launcher.

---

## 🛠️ Web App Getting Started

### Prerequisites
Make sure you have [Node.js](https://nodejs.org/) installed.

### Installation & Development
1. **Clone the repository**:
   ```bash
   git clone https://github.com/AbhijitSarode/Shutup-Study.git
   cd Shutup-Study
   ```
2. **Install dependencies**:
   ```bash
   npm install
   ```
3. **Run local dev server**:
   ```bash
   npm run dev
   ```
4. **Compile production build**:
   ```bash
   npm run build
   ```

---

## 🔧 Firebase Setup

1. Enable **Cloud Firestore** in your Firebase project.
2. Update the credentials in `src/firebase.js` with your project's configuration.
3. Deploy the database rules found in `firestore.rules` inside your Firebase Console Rules tab to authorize read, write, and delete operations correctly.
