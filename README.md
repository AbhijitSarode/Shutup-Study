# 🧠 Shutup & Study

🚀 **[Open Web Application](https://abhijitsarode.github.io/Shutup-Study/)**

A collaborative, distraction-free Pomodoro study room built with React, Vite, and Firebase Firestore. Designed to help study buddies co-work in real time with minimum friction.

---

## ✨ Features

- **🎨 Minimalist Neumorphic UI**: Embossed cards, debossed inputs, and clean layouts that adapt to both **Light** and **Dark** system preferences.
- **🔄 Real-Time Synchronized Timers**: High-accuracy client-side countdowns synced to Firestore server timestamps, avoiding write rate limits while keeping everyone perfectly aligned.
- **⚡ Hands-Free Auto-Transitions**: Timer phases automatically advance (Study ➔ Short Break ➔ Long Break) to minimize user interactions so you can stay in the zone.
- **🎵 Phase-Specific Synthesizer Chimes**: Dynamic audio chimes generated using the Web Audio API (ascending melody for Focus, relaxing tones for Break, and an uplifting arpeggio for Long Break).
- **📋 Real-Time Leader Actions Log**: Instant status updates showing exactly what the room leader did (e.g. started, paused, skipped, or updated settings).
- **🏆 Set Completion Flow**: A congratulatory overlay modal triggers once a full Pomodoro cycle is finished. The leader can restart the cycle or discard/close the room (which redirects everyone back home).
- **📱 Fully Responsive**: Custom layout queries that hide the Study Buddies list on mobile viewports, leaving the Pomodoro clock as the sole focus element.

---

## 🛠️ Tech Stack

- **Frontend**: React, Vite
- **Styling**: Custom CSS (Vanilla variables & Neumorphic design tokens)
- **Database**: Cloud Firestore (Real-time snapshot subscriptions)
- **Assets**: Lucide React Icons, Canvas Confetti

---

## 🚀 Getting Started

### Prerequisites

Make sure you have [Node.js](https://nodejs.org/) installed.

### Installation

1. **Clone the repository:**
   ```bash
   git clone https://github.com/AbhijitSarode/Shutup-Study.git
   cd Shutup-Study
   ```

2. **Install dependencies:**
   ```bash
   npm install
   ```

3. **Run the local development server:**
   ```bash
   npm run dev
   ```

4. **Build the production bundle:**
   ```bash
   npm run build
   ```

---

## 🔧 Firebase Setup

1. Enable the **Cloud Firestore** database in your Firebase project.
2. Update the credentials in `src/firebase.js` with your project's configuration.
3. Deploy the database rules found in `firestore.rules` inside your Firebase Console Rules tab to authorize read, write, and delete operations correctly.
