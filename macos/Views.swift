import SwiftUI

// 1. Color Palette Extension matching the CSS Design tokens
extension Color {
    static let neuBg = Color(red: 0.941, green: 0.941, blue: 0.953) // #f0f0f3
    static let neuDarkShadow = Color(red: 0.745, green: 0.745, blue: 0.776) // #bebebe
    static let neuLightShadow = Color.white
    static let neuTextPrimary = Color(red: 0.20, green: 0.20, blue: 0.21) // #333336
    static let neuTextSecondary = Color(red: 0.49, green: 0.49, blue: 0.51) // #7e7e82
    static let neuTextMuted = Color(red: 0.63, green: 0.63, blue: 0.65) // #a0a0a5
    
    // Pastels
    static let focusColor = Color(red: 1.0, green: 0.42, blue: 0.42) // #ff6b6b
    static let breakColor = Color(red: 0.30, green: 0.68, blue: 0.97) // #4dadf7
    static let longBreakColor = Color(red: 0.32, green: 0.81, blue: 0.40) // #51cf66
    
    // Emerald Neumorphic Tones
    static let emeraldBg = Color(red: 0.925, green: 0.984, blue: 0.957) // #ecfdf5
    static let emeraldText = Color(red: 0.024, green: 0.373, blue: 0.275) // #065f46
}

// 2. Neumorphic View Modifiers
struct NeuFlat: ViewModifier {
    var radius: CGFloat = 16
    var bgColor: Color = .neuBg
    
    func body(content: Content) -> some View {
        content
            .background(bgColor)
            .cornerRadius(radius)
            .shadow(color: Color.neuDarkShadow.opacity(0.8), radius: 8, x: 6, y: 6)
            .shadow(color: Color.neuLightShadow.opacity(0.9), radius: 8, x: -6, y: -6)
    }
}

struct NeuPressed: ViewModifier {
    var radius: CGFloat = 12
    func body(content: Content) -> some View {
        content
            .background(Color.neuBg)
            .cornerRadius(radius)
            .overlay(
                RoundedRectangle(cornerRadius: radius)
                    .stroke(Color.neuBg, lineWidth: 4)
                    .shadow(color: Color.neuDarkShadow.opacity(0.6), radius: 4, x: 3, y: 3)
                    .clipShape(RoundedRectangle(cornerRadius: radius))
            )
            .overlay(
                RoundedRectangle(cornerRadius: radius)
                    .stroke(Color.neuBg, lineWidth: 4)
                    .shadow(color: Color.white.opacity(0.9), radius: 4, x: -3, y: -3)
                    .clipShape(RoundedRectangle(cornerRadius: radius))
            )
    }
}

// 3. Custom UI Elements
struct NeuTextField: View {
    var placeholder: String
    @Binding var text: String
    
    var body: some View {
        TextField("", text: $text)
            .placeholder(when: text.isEmpty) {
                Text(placeholder).foregroundColor(.neuTextMuted)
            }
            .textFieldStyle(.plain)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .font(.system(size: 14, weight: .medium, design: .default))
            .foregroundColor(.neuTextPrimary)
            .modifier(NeuPressed(radius: 12))
    }
}

extension View {
    func placeholder<Content: View>(
        when shouldShow: Bool,
        alignment: Alignment = .leading,
        @ViewBuilder placeholder: () -> Content) -> some View {
        ZStack(alignment: alignment) {
            placeholder().opacity(shouldShow ? 1 : 0).padding(.leading, 18)
            self
        }
    }
}

struct NeuButton<Content: View>: View {
    var action: () -> Void
    var content: Content
    var bgColor: Color = .neuBg
    
    init(action: @escaping () -> Void, bgColor: Color = .neuBg, @ViewBuilder content: () -> Content) {
        self.action = action
        self.bgColor = bgColor
        self.content = content()
    }
    
    var body: some View {
        Button(action: {
            action()
        }) {
            content
                .foregroundColor(.neuTextPrimary)
                .padding(.vertical, 12)
                .padding(.horizontal, 16)
                .frame(maxWidth: .infinity)
                .modifier(NeuFlat(radius: 12, bgColor: bgColor))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct NeuCircleButton<Content: View>: View {
    var action: () -> Void
    var content: Content
    var size: CGFloat = 44
    var activeColor: Color? = nil
    
    init(action: @escaping () -> Void, size: CGFloat = 44, activeColor: Color? = nil, @ViewBuilder content: () -> Content) {
        self.action = action
        self.size = size
        self.activeColor = activeColor
        self.content = content()
    }
    
    var body: some View {
        Button(action: {
            action()
        }) {
            ZStack {
                if let color = activeColor {
                    Circle()
                        .fill(color.opacity(0.12))
                        .frame(width: size, height: size)
                    Circle()
                        .stroke(color.opacity(0.3), lineWidth: 1)
                        .frame(width: size, height: size)
                } else {
                    Circle()
                        .fill(Color.neuBg)
                        .frame(width: size, height: size)
                        .shadow(color: Color.neuDarkShadow.opacity(0.8), radius: 4, x: 3, y: 3)
                        .shadow(color: Color.white.opacity(0.9), radius: 4, x: -3, y: -3)
                }
                
                content
                    .foregroundColor(activeColor ?? .neuTextPrimary)
            }
            .background(Color.white.opacity(0.001))
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
    }
}

// 4. Timer Dial View
struct TimerDialView: View {
    var secondsRemaining: Int
    var totalSeconds: Int
    var phase: String
    var interval: Int
    var totalIntervals: Int
    
    var progress: Double {
        if totalSeconds <= 0 { return 0 }
        return Double(totalSeconds - secondsRemaining) / Double(totalSeconds)
    }
    
    var timeString: String {
        let mins = secondsRemaining / 60
        let secs = secondsRemaining % 60
        return String(format: "%02d:%02d", mins, secs)
    }
    
    var phaseTitle: String {
        switch phase {
        case "shortBreak": return "SHORT BREAK"
        case "longBreak": return "LONG BREAK"
        default: return "FOCUS PHASE"
        }
    }
    
    var phaseColor: Color {
        switch phase {
        case "shortBreak": return .breakColor
        case "longBreak": return .longBreakColor
        default: return .focusColor
        }
    }
    
    var body: some View {
        VStack(spacing: 24) {
            ZStack {
                // 1. Large elevated circular block (outer)
                Circle()
                    .fill(Color.neuBg)
                    .frame(width: 300, height: 300)
                    .shadow(color: Color.neuDarkShadow.opacity(0.8), radius: 8, x: 6, y: 6)
                    .shadow(color: Color.white.opacity(0.9), radius: 8, x: -6, y: -6)
                
                // 2. Dial track (sunken circular incess trench) - very soft and smooth
                ZStack {
                    Circle()
                        .stroke(Color.neuBg, lineWidth: 24)
                    
                    // Soft dark inner shadow on top-left wall
                    Circle()
                        .stroke(Color.neuDarkShadow.opacity(0.65), lineWidth: 6)
                        .offset(x: -2, y: -2)
                        .blur(radius: 3)
                        .mask(Circle().stroke(lineWidth: 24))
                    
                    // Soft light inner shadow on bottom-right wall
                    Circle()
                        .stroke(Color.white.opacity(0.95), lineWidth: 6)
                        .offset(x: 2, y: 2)
                        .blur(radius: 3)
                        .mask(Circle().stroke(lineWidth: 24))
                }
                .frame(width: 230, height: 230)
                
                // 3. Progress track stroke (runs inside the incess)
                Circle()
                    .trim(from: 0.0, to: progress)
                    .stroke(
                        phaseColor,
                        style: StrokeStyle(lineWidth: 10, lineCap: .round)
                    )
                    .rotationEffect(Angle(degrees: -90))
                    .frame(width: 230, height: 230)
                    .animation(.linear(duration: 1.0), value: secondsRemaining)
                
                // 4. Flat inner circular block (covers the inner edge of the track, diameter = 230 - 24 = 206)
                Circle()
                    .fill(Color.neuBg)
                    .frame(width: 206, height: 206)
                
                // 5. Inner information area
                VStack(spacing: 4) {
                    Text(timeString)
                        .font(.system(size: 52, weight: .heavy, design: .rounded))
                        .foregroundColor(.neuTextPrimary)
                    
                    Text(phaseTitle)
                        .font(.system(size: 11, weight: .bold, design: .default))
                        .foregroundColor(.neuTextSecondary)
                        .tracking(1.5)
                }
            }
        }
    }
}

// 5. Pulsating Sync Status indicator
struct PulsatingDot: View {
    var color: Color
    @State private var isAnimating = false
    
    var body: some View {
        ZStack {
            Circle()
                .fill(color.opacity(0.35))
                .frame(width: 14, height: 14)
                .scaleEffect(isAnimating ? 1.6 : 1.0)
                .opacity(isAnimating ? 0.0 : 1.0)
            
            Circle()
                .fill(color)
                .frame(width: 8, height: 8)
        }
        .onAppear {
            withAnimation(Animation.easeInOut(duration: 1.2).repeatForever(autoreverses: false)) {
                isAnimating = true
            }
        }
    }
}

// 6. Participants List View
struct ParticipantsListView: View {
    var participants: [Participant]
    var currentUserId: String
    var currentPhase: String
    
    var phaseColor: Color {
        switch currentPhase {
        case "shortBreak": return .breakColor
        case "longBreak": return .longBreakColor
        default: return .focusColor
        }
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 8) {
                Image(systemName: "person.2.fill")
                    .font(.system(size: 14))
                    .foregroundColor(.neuTextSecondary)
                Text("Study Buddies (\(participants.count))")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.neuTextSecondary)
            }
            .padding(.bottom, 6)
            .frame(maxWidth: .infinity, alignment: .leading)
            .overlay(
                Rectangle()
                    .frame(height: 1)
                    .foregroundColor(Color.neuDarkShadow.opacity(0.3)),
                alignment: .bottom
            )
            
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 12) {
                    if participants.isEmpty {
                        Text("Waiting for study buddies...")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.neuTextMuted)
                            .padding(.vertical, 32)
                            .frame(maxWidth: .infinity)
                    } else {
                        ForEach(participants) { buddy in
                            let isBuddyLeader = buddy.role == "leader"
                            let isBuddySelf = buddy.id == currentUserId
                            
                            HStack(spacing: 12) {
                                // Avatar circle with inset shadow appearance
                                ZStack {
                                    Circle()
                                        .fill(Color.neuBg)
                                        .frame(width: 36, height: 36)
                                        .modifier(NeuPressed(radius: 18))
                                    
                                    Image(systemName: isBuddyLeader ? "shield.fill" : "person.fill")
                                        .font(.system(size: 13))
                                        .foregroundColor(isBuddyLeader ? .orange : .neuTextSecondary)
                                }
                                
                                VStack(alignment: .leading, spacing: 2) {
                                    HStack(spacing: 4) {
                                        Text(buddy.name)
                                            .font(.system(size: 13, weight: .bold))
                                            .foregroundColor(.neuTextPrimary)
                                        if isBuddySelf {
                                            Text("(You)")
                                                .font(.system(size: 10))
                                                .foregroundColor(.neuTextMuted)
                                        }
                                    }
                                    
                                    Text(isBuddyLeader ? "Leader" : "Studying")
                                        .font(.system(size: 11))
                                        .foregroundColor(.neuTextSecondary)
                                }
                                
                                Spacer()
                                
                                // Pulsating sync indicator
                                PulsatingDot(color: phaseColor)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 10)
                            .modifier(NeuFlat(radius: 12))
                        }
                    }
                }
                .padding(.vertical, 4)
                .padding(.horizontal, 2)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .modifier(NeuFlat(radius: 20))
    }
}
