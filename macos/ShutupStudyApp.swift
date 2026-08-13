import SwiftUI
import AppKit

class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        // Disable window saved state restoration to ignore old 500px height cache
        UserDefaults.standard.register(defaults: ["NSQuitAlwaysKeepsWindows": false])

        DispatchQueue.main.async {
            if let window = NSApplication.shared.windows.first {
                var currentFrame = window.frame
                currentFrame.size.width = 900
                currentFrame.size.height = 700
                window.setFrame(currentFrame, display: true, animate: false)
                window.minSize = NSSize(width: 850, height: 650)
                window.maxSize = NSSize(width: 1200, height: 950)
            }
        }
    }
}

@main
struct ShutupStudyApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var viewModel = SessionViewModel.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(viewModel)
                .frame(minWidth: 850, idealWidth: 900, maxWidth: 1200, minHeight: 650, idealHeight: 700, maxHeight: 950)
                .background(Color.neuBg.ignoresSafeArea())
        }
        .windowStyle(.hiddenTitleBar)

        // Menu Bar Integration
        MenuBarExtra {
            MenuBarView(viewModel: viewModel)
        } label: {
            HStack(spacing: 4) {
                Image(systemName: "timer")
                if viewModel.step == "active-session" && !viewModel.roomId.isEmpty {
                    Text("\(viewModel.timeString) (\(viewModel.currentPhaseShort))")
                }
            }
        }
    }
}

// 1. Menu Bar View dropdown contents
struct MenuBarView: View {
    @ObservedObject var viewModel: SessionViewModel

    var body: some View {
        if viewModel.step == "active-session" && !viewModel.roomId.isEmpty {
            Text("Session Room: \(viewModel.roomId)")
            Text("Status: \(viewModel.status == "running" ? "Running" : "Paused")")
            Text("Phase: \(viewModel.currentPhaseTitle)")
            Text("Interval: \(viewModel.currentInterval) of \(viewModel.totalIntervals)")

            Divider()

            if viewModel.isLeader {
                Button(viewModel.status == "running" ? "Pause Timer" : "Resume Timer") {
                    viewModel.toggleTimer()
                }
                Button("Skip Phase") {
                    viewModel.skipPhase()
                }
                Button("Reset Timer") {
                    viewModel.resetTimer()
                }
                Divider()
            }
        } else {
            Text("Shutup & Study")
            Text("No active session running")
            Divider()
        }

        Button("Quit App") {
            NSApplication.shared.terminate(nil)
        }
    }
}

// 2. Navigation Routing ContentView
struct ContentView: View {
    @EnvironmentObject var viewModel: SessionViewModel

    var body: some View {
        ZStack {
            Color.neuBg.ignoresSafeArea()

            switch viewModel.step {
            case "join-room-gate":
                JoinGateView()
                    .frame(maxWidth: 420)
                    .padding(.horizontal, 24)
                    .padding(.top, 32)
                    .padding(.bottom, 32)
                    .ignoresSafeArea()
            case "active-session":
                ActiveSessionView()
                    .padding(.horizontal, 24)
                    .padding(.top, 32)
                    .padding(.bottom, 32)
                    .ignoresSafeArea()
            default:
                HomeView()
                    .frame(maxWidth: 450)
                    .padding(.horizontal, 24)
                    .padding(.top, 32)
                    .padding(.bottom, 32)
                    .ignoresSafeArea()
            }
        }
    }
}

// 3. Home Dashboard View
struct HomeView: View {
    @EnvironmentObject var viewModel: SessionViewModel
    @State private var creationSetupActive = false

    var body: some View {
        VStack(spacing: 24) {
            // Brand Logo & Title
            VStack(spacing: 8) {
                ZStack {
                    Circle()
                        .fill(Color.neuBg)
                        .frame(width: 60, height: 60)
                        .shadow(color: Color.neuDarkShadow.opacity(0.8), radius: 6, x: 4, y: 4)
                        .shadow(color: Color.white.opacity(0.9), radius: 6, x: -4, y: -4)

                    Image(systemName: "book.fill")
                        .font(.system(size: 24))
                        .foregroundColor(.neuTextPrimary)
                }

                Text("Shutup & Study")
                    .font(.system(size: 26, weight: .heavy, design: .rounded))
                    .foregroundColor(.neuTextPrimary)

                Text("SYNCHRONIZED STUDY ROOMS")
                    .font(.system(size: 9, weight: .bold))
                    .foregroundColor(.neuTextSecondary)
                    .tracking(1.5)
            }
            .padding(.top, 8)

            if !viewModel.joinError.isEmpty {
                Text(viewModel.joinError)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(.focusColor)
                    .padding(12)
                    .frame(maxWidth: .infinity)
                    .background(Color.focusColor.opacity(0.1))
                    .cornerRadius(12)
            }

            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 24) {
                    if !creationSetupActive {
                        // 1. Solo Study Timer Card
                        VStack(alignment: .leading, spacing: 18) {
                            HStack(spacing: 8) {
                                Text("⏱️")
                                    .font(.system(size: 18))
                                Text("Solo Study Session")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(.neuTextPrimary)
                            }

                            Text("Study alone with a simple, distraction-free offline Pomodoro timer. No internet or database connection required.")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.neuTextSecondary)
                                .lineSpacing(4)

                            NeuButton(action: {
                                viewModel.startSoloSession()
                            }) {
                                Text("Start Solo Timer")
                                    .font(.system(size: 13, weight: .bold))
                            }
                            .padding(.top, 4)
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 24)
                        .modifier(NeuFlat(radius: 16))

                        // 2. Group Study Session Card
                        VStack(alignment: .leading, spacing: 18) {
                            HStack(spacing: 8) {
                                Text("👥")
                                    .font(.system(size: 18))
                                Text("Group Study Session")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(.neuTextPrimary)
                            }

                            Text("Study together with friends in a synchronized room. Enter a room code to join, or create a new room session.")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.neuTextSecondary)
                                .lineSpacing(4)

                            NeuTextField(placeholder: "Enter Room Code", text: $viewModel.inputRoomId)

                            VStack(spacing: 12) {
                                NeuButton(action: {
                                    viewModel.prepareToJoin()
                                }) {
                                    Text("Join Room")
                                        .font(.system(size: 13, weight: .bold))
                                }
                                .disabled(viewModel.inputRoomId.isEmpty)

                                NeuButton(action: {
                                    withAnimation { creationSetupActive = true }
                                }) {
                                    Text("Create New Room")
                                        .font(.system(size: 13, weight: .bold))
                                }
                            }
                            .padding(.top, 4)
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 24)
                        .modifier(NeuFlat(radius: 16))
                    } else {
                        // Section C: Creation template config form (Study Setup)
                        VStack(spacing: 16) {
                            VStack(spacing: 16) {
                                // Title with Shield Icon
                                VStack(spacing: 6) {
                                    HStack(spacing: 8) {
                                        Image(systemName: "shield.fill")
                                            .font(.system(size: 18))
                                            .foregroundColor(Color(red: 0.5, green: 0.65, blue: 0.75))
                                        Text("Study Setup")
                                            .font(.system(size: 18, weight: .bold))
                                            .foregroundColor(.neuTextPrimary)
                                    }

                                    Text("Configure your group's pomodoro.")
                                        .font(.system(size: 11, weight: .medium))
                                        .foregroundColor(.neuTextSecondary)
                                }
                                .padding(.bottom, 4)

                                // User display name field
                                VStack(alignment: .leading, spacing: 6) {
                                    Text("YOUR DISPLAY NAME")
                                        .font(.system(size: 9, weight: .bold))
                                        .foregroundColor(.neuTextSecondary)
                                        .tracking(1)
                                    NeuTextField(placeholder: "e.g. Abhijit", text: $viewModel.userName)
                                }

                                // Grid of Picker values
                                HStack(spacing: 12) {
                                    PickerCell(title: "FOCUS (MIN)", val: $viewModel.focusTime, range: 1...60)
                                    PickerCell(title: "BREAK (MIN)", val: $viewModel.shortBreak, range: 1...30)
                                }

                                HStack(spacing: 12) {
                                    PickerCell(title: "LONG BREAK (MIN)", val: $viewModel.longBreak, range: 1...45)
                                    PickerCell(title: "INTERVAL COUNT", val: $viewModel.totalIntervals, range: 2...8)
                                }
                                .padding(.bottom, 6)

                                // Submit button in emerald styling
                                NeuButton(action: {
                                    viewModel.createRoom()
                                }, bgColor: .emeraldBg) {
                                    Text(viewModel.isSubmitting ? "Initializing..." : "Start & Create Room")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(.emeraldText)
                                }
                                .disabled(viewModel.userName.isEmpty || viewModel.isSubmitting)
                            }
                            .padding(24)
                            .modifier(NeuFlat(radius: 20))

                            // Cancel/Back Link
                            Button(action: {
                                withAnimation { creationSetupActive = false }
                            }) {
                                Text("Back to Dashboard")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(.neuTextSecondary)
                            }
                            .buttonStyle(.plain)
                            .padding(.top, 4)
                        }
                    }
                }
                .padding(.vertical, 8)
                .padding(.horizontal, 4)
            }
        }
    }
}

// Subcomponent: Mini-stepper picker cell
struct PickerCell: View {
    var title: String
    @Binding var val: Int
    var range: ClosedRange<Int>

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.system(size: 9, weight: .bold))
                .foregroundColor(.neuTextSecondary)

            TextField("", value: $val, format: .number)
                .textFieldStyle(.plain)
                .multilineTextAlignment(.center)
                .font(.system(size: 13, weight: .bold, design: .rounded))
                .foregroundColor(.neuTextPrimary)
                .padding(.vertical, 8)
                .modifier(NeuPressed(radius: 8))
                .onChange(of: val) { newValue in
                    if newValue < range.lowerBound {
                        val = range.lowerBound
                    } else if newValue > range.upperBound {
                        val = range.upperBound
                    }
                }
        }
        .frame(maxWidth: .infinity)
    }
}

// 4. Join Gate View (when guest joins room without username cached)
struct JoinGateView: View {
    @EnvironmentObject var viewModel: SessionViewModel

    var body: some View {
        VStack(spacing: 24) {
            VStack(spacing: 8) {
                Text("Identify Yourself")
                    .font(.system(size: 20, weight: .bold, design: .rounded))
                    .foregroundColor(.neuTextPrimary)
                Text("You are joining Room: \(viewModel.roomId)")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.neuTextSecondary)
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("YOUR NAME")
                    .font(.system(size: 9, weight: .bold))
                    .foregroundColor(.neuTextSecondary)

                NeuTextField(placeholder: "e.g. Abhijit", text: $viewModel.userName)
            }

            NeuButton(action: {
                viewModel.joinRoom()
            }) {
                Text("Join Study Room")
                    .font(.system(size: 13, weight: .bold))
            }
            .disabled(viewModel.userName.isEmpty)

            Button("Back to Dashboard") {
                viewModel.step = "home"
            }
            .buttonStyle(.plain)
            .foregroundColor(.neuTextSecondary)
            .font(.system(size: 12, weight: .medium))
        }
        .padding(24)
        .modifier(NeuFlat(radius: 16))
    }
}

// 5. Active Session Timer View
struct ActiveSessionView: View {
    @EnvironmentObject var viewModel: SessionViewModel
    @State private var copied = false

    var actionStatusText: String {
        if viewModel.status == "running" {
            return "▶️ Session is running"
        } else if viewModel.status == "paused" {
            return "⏸️ Session is paused"
        } else {
            return "🕒 Room created. Waiting to start."
        }
    }

    var body: some View {
        HStack(spacing: 24) {
            if viewModel.isSoloMode {
                Spacer()
            } else {
                // Left Panel: Participants List (1/3 Width)
                ParticipantsListView(
                    participants: viewModel.participants,
                    currentUserId: viewModel.userId,
                    currentPhase: viewModel.currentPhase
                )
                .frame(width: 260)
                .frame(maxHeight: .infinity)
            }

            // Right Panel: Timer Card & Controls
            VStack(spacing: 16) {
                // Header row inside Timer card
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 3) {
                        if viewModel.isSoloMode {
                            Text("Solo Study Session")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.neuTextPrimary)

                            Text("Local offline timer")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(.neuTextSecondary)
                        } else {
                            HStack(spacing: 8) {
                                Text("Room: \(viewModel.roomId)")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(.neuTextPrimary)

                                Button(action: {
                                    let pasteboard = NSPasteboard.general
                                    pasteboard.clearContents()
                                    pasteboard.setString(viewModel.roomId, forType: .string)
                                    withAnimation {
                                        copied = true
                                    }
                                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                                        copied = false
                                    }
                                }) {
                                    Image(systemName: copied ? "checkmark.circle.fill" : "doc.on.doc.fill")
                                        .font(.system(size: 11))
                                        .foregroundColor(copied ? .green : .neuTextSecondary)
                                        .frame(width: 24, height: 24)
                                        .background(Color.white.opacity(0.001))
                                        .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                            }

                            Text("Interval: \(viewModel.currentInterval)/\(viewModel.totalIntervals)")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(.neuTextSecondary)
                        }
                    }

                    Spacer()

                    // Session Action buttons (Mute, Settings, Leave)
                    HStack(spacing: 12) {
                        NeuCircleButton(action: {
                            viewModel.soundEnabled.toggle()
                        }, activeColor: viewModel.soundEnabled ? Color.breakColor : nil) {
                            Image(systemName: viewModel.soundEnabled ? "volume.3.fill" : "volume.slash.fill")
                                .font(.system(size: 12))
                        }

                        if viewModel.isLeader {
                            NeuCircleButton(action: {
                                viewModel.showSettings = true
                            }) {
                                Image(systemName: "gearshape.fill")
                                    .font(.system(size: 12))
                            }
                        }

                        NeuCircleButton(action: {
                            viewModel.leaveRoom()
                        }) {
                            Image(systemName: "rectangle.portrait.and.arrow.forward.fill")
                                .font(.system(size: 12))
                                .foregroundColor(.focusColor)
                        }
                    }
                }

                Spacer()

                // Circular Timer Dial
                TimerDialView(
                    secondsRemaining: viewModel.timerSecondsRemaining,
                    totalSeconds: viewModel.totalSeconds,
                    phase: viewModel.currentPhase,
                    interval: viewModel.currentInterval,
                    totalIntervals: viewModel.totalIntervals
                )

                Spacer()

                // Neumorphic Action log bar
                Text(actionStatusText)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(.neuTextSecondary)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .frame(minWidth: 260, maxWidth: 260, minHeight: 32)
                    .background(Color.neuBg)
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.neuBg, lineWidth: 3)
                            .shadow(color: Color.neuDarkShadow.opacity(0.5), radius: 3, x: 2, y: 2)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.neuBg, lineWidth: 3)
                            .shadow(color: Color.white.opacity(0.8), radius: 3, x: -2, y: -2)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    )

                // Playback Actions (Leader Controls or controlled message)
                if viewModel.isLeader {
                    HStack(spacing: 16) {
                        NeuCircleButton(action: {
                            viewModel.toggleTimer()
                        }, size: 54, activeColor: viewModel.status == "running" ? Color.focusColor : nil) {
                            Image(systemName: viewModel.status == "running" ? "pause.fill" : "play.fill")
                                .font(.system(size: 18, weight: .bold))
                        }

                        NeuCircleButton(action: {
                            viewModel.resetTimer()
                        }, size: 54) {
                            Image(systemName: "arrow.counterclockwise")
                                .font(.system(size: 16, weight: .bold))
                        }

                        NeuCircleButton(action: {
                            viewModel.skipPhase()
                        }, size: 54) {
                            Image(systemName: "forward.fill")
                                .font(.system(size: 16, weight: .bold))
                        }
                    }
                    .padding(.bottom, 4)
                } else {
                    HStack(spacing: 6) {
                        Image(systemName: "shield.fill")
                            .font(.system(size: 12))
                            .foregroundColor(.orange)
                        Text("Controlled by Leader: \(viewModel.userName)")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.neuTextSecondary)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .modifier(NeuFlat(radius: 12))
                    .padding(.bottom, 4)
                }

                Spacer()
            }
            .padding(20)
            .frame(maxWidth: viewModel.isSoloMode ? 520 : .infinity, maxHeight: .infinity)
            .modifier(NeuFlat(radius: 20))

            if viewModel.isSoloMode {
                Spacer()
            }
        }
        .overlay(
            Group {
                if viewModel.showSettings {
                    ZStack {
                        Color.black.opacity(0.35)
                            .ignoresSafeArea()

                        VStack(spacing: 0) {
                            // Header Row
                            HStack {
                                HStack(spacing: 8) {
                                    Image(systemName: "pencil")
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundColor(.neuTextSecondary)
                                    Text("Edit Pomodoro Template")
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(.neuTextSecondary)
                                }

                                Spacer()

                                // Circular gear button (closes panel)
                                Button(action: {
                                    viewModel.showSettings = false
                                }) {
                                    ZStack {
                                        Circle()
                                            .fill(Color.neuBg)
                                            .frame(width: 32, height: 32)
                                            .shadow(color: Color.neuDarkShadow.opacity(0.8), radius: 3, x: 2, y: 2)
                                            .shadow(color: Color.white.opacity(0.9), radius: 3, x: -2, y: -2)

                                        Image(systemName: "gearshape.fill")
                                            .font(.system(size: 12))
                                            .foregroundColor(.neuTextSecondary)
                                    }
                                }
                                .buttonStyle(.plain)
                            }
                            .padding(.bottom, 24)

                            // 2x2 Grid of settings
                            VStack(spacing: 16) {
                                HStack(spacing: 16) {
                                    PickerCell(title: "Study Time (min)", val: $viewModel.focusTime, range: 1...60)
                                    PickerCell(title: "Short Break (min)", val: $viewModel.shortBreak, range: 1...30)
                                }

                                HStack(spacing: 16) {
                                    PickerCell(title: "Long Break (min)", val: $viewModel.longBreak, range: 1...45)
                                    PickerCell(title: "Interval Count", val: $viewModel.totalIntervals, range: 2...8)
                                }
                            }
                            .padding(.bottom, 28)

                            // Actions
                            HStack(spacing: 16) {
                                NeuButton(action: {
                                    viewModel.saveSettingsInDb()
                                }, bgColor: .emeraldBg) {
                                    Text("Save & Reset")
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(.emeraldText)
                                }

                                NeuButton(action: {
                                    viewModel.showSettings = false
                                }) {
                                    Text("Cancel")
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(.neuTextSecondary)
                                }
                            }
                        }
                        .padding(28)
                        .background(Color.neuBg)
                        .cornerRadius(24)
                        .overlay(
                            RoundedRectangle(cornerRadius: 24)
                                .stroke(Color.neuDarkShadow.opacity(0.35), lineWidth: 1)
                        )
                        .shadow(color: viewModel.isSoloMode ? .clear : Color.neuDarkShadow.opacity(0.8), radius: 8, x: 6, y: 6)
                        .shadow(color: viewModel.isSoloMode ? .clear : Color.white.opacity(0.9), radius: 8, x: -6, y: -6)
                        .frame(width: 500, height: 390) // Enforces 900x700 aspect ratio: 500x390 is 1.28 ratio (same as 900/700 = 1.28)
                    }
                }
            }
        )
        .overlay(
            Group {
                if viewModel.cycleCompleted {
                    ZStack {
                        Color.black.opacity(0.35)
                            .ignoresSafeArea()

                        VStack(spacing: 16) {
                            // Trophy Emoji
                            Text("🏆")
                                .font(.system(size: 32))
                                .padding(.top, 8)

                            // Heading
                            Text("Pomodoro Cycle Complete!")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(.neuTextPrimary)

                            // Subtitle
                            Text("Outstanding work! You successfully finished the entire study cycle. Would you like to start a new cycle or close this room?")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(.neuTextSecondary)
                                .multilineTextAlignment(.center)
                                .lineSpacing(4)
                                .padding(.horizontal, 16)
                                .padding(.bottom, 12)

                            // Action Buttons (Leader only, Guest waits)
                            if viewModel.isLeader {
                                VStack(spacing: 12) {
                                    NeuButton(action: {
                                        viewModel.continueCycle()
                                    }, bgColor: .emeraldBg) {
                                        Text("Start New Cycle")
                                            .font(.system(size: 13, weight: .bold))
                                            .foregroundColor(.emeraldText)
                                    }

                                    NeuButton(action: {
                                        viewModel.leaveRoom()
                                    }) {
                                        Text("Discard & Close Room")
                                            .font(.system(size: 13, weight: .bold))
                                            .foregroundColor(.focusColor)
                                    }
                                }
                            } else {
                                Text("Waiting for the room leader to restart...")
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundColor(.neuTextSecondary)
                                    .italic()
                                    .padding(.top, 10)
                            }
                        }
                        .padding(28)
                        .modifier(NeuFlat(radius: 24))
                        .frame(width: 480, height: 373) // Enforces 900x700 aspect ratio (480x373 is 1.28)
                    }
                }
            }
        )
    }
}
