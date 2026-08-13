import Foundation
import Combine

class SessionViewModel: ObservableObject {
    static let shared = SessionViewModel()
    
    @Published var step: String = "home" // "home" | "join-room-gate" | "active-session"
    @Published var roomId: String = ""
    @Published var userId: String = ""
    @Published var userName: String = ""
    @Published var isLeader: Bool = false
    
    // Timer state
    @Published var timerSecondsRemaining: Int = 1500
    @Published var totalSeconds: Int = 1500
    @Published var currentPhase: String = "focus" // "focus" | "shortBreak" | "longBreak"
    @Published var currentInterval: Int = 1
    @Published var status: String = "idle" // "idle" | "running" | "paused"
    @Published var cycleCompleted: Bool = false
    
    @Published var participants: [Participant] = []
    @Published var soundEnabled: Bool = true
    
    // Template settings (in minutes)
    @Published var focusTime: Int = 25
    @Published var shortBreak: Int = 5
    @Published var longBreak: Int = 15
    @Published var totalIntervals: Int = 4 // longBreakInterval
    
    @Published var inputRoomId: String = ""
    @Published var joinError: String = ""
    @Published var isSubmitting: Bool = false
    @Published var showSettings: Bool = false
    @Published var isSoloMode: Bool = false
    
    // Timer details
    private var lastPhase: String = ""
    private var countdownTimer: Timer?
    private var dbPollTimer: Timer?
    private var participantsPollTimer: Timer?
    private var heartbeatTimer: Timer?
    
    var timeString: String {
        let mins = timerSecondsRemaining / 60
        let secs = timerSecondsRemaining % 60
        return String(format: "%02d:%02d", mins, secs)
    }
    
    var currentPhaseShort: String {
        switch currentPhase {
        case "shortBreak": return "S.Break"
        case "longBreak": return "L.Break"
        default: return "Focus"
        }
    }
    
    var currentPhaseTitle: String {
        switch currentPhase {
        case "shortBreak": return "Short Break"
        case "longBreak": return "Long Break"
        default: return "Focusing"
        }
    }
    
    init() {
        // Load persistent userId
        if let cachedUid = UserDefaults.standard.string(forKey: "shutup_study_uid") {
            userId = cachedUid
        } else {
            userId = "usr_" + UUID().uuidString.prefix(12).lowercased()
            UserDefaults.standard.set(userId, forKey: "shutup_study_uid")
        }
        
        // Load username
        let cachedName = UserDefaults.standard.string(forKey: "shutup_study_username") ?? ""
        if cachedName.isEmpty {
            let sysName = NSFullUserName()
            userName = sysName.isEmpty ? "Study Buddy" : sysName
            UserDefaults.standard.set(userName, forKey: "shutup_study_username")
        } else {
            userName = cachedName
        }
    }
    
    // Save display name
    func saveUserName(_ name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        userName = trimmed
        UserDefaults.standard.set(trimmed, forKey: "shutup_study_username")
    }
    
    // 1. Create Room Action
    func createRoom() {
        guard !userName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        isSubmitting = true
        joinError = ""
        
        saveUserName(userName)
        
        // Generate random 6-character uppercase Room Code
        let letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        let newRoomId = String((0..<6).map { _ in letters.randomElement()! })
        
        let template = SessionTemplate(
            focusTime: focusTime * 60,
            shortBreakTime: shortBreak * 60,
            longBreakTime: longBreak * 60,
            longBreakInterval: totalIntervals
        )
        
        FirestoreClient.shared.createSession(roomId: newRoomId, leaderId: userId, leaderName: userName, template: template) { [weak self] result in
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.isSubmitting = false
                
                switch result {
                case .success(let session):
                    self.isLeader = true
                    self.roomId = session.roomId
                    self.timerSecondsRemaining = session.state.timerSecondsRemaining
                    self.totalSeconds = template.focusTime
                    self.status = "running"
                    self.currentPhase = "focus"
                    self.currentInterval = 1
                    self.cycleCompleted = false
                    self.lastPhase = "focus"
                    
                    // Join subcollection
                    self.joinParticipantSubcollection()
                    
                    self.step = "active-session"
                    self.startBackgroundTasks()
                    
                    // Immediately trigger a state update to Firestore to start the timer in the database
                    let startState = SessionState(
                        status: "running",
                        currentPhase: "focus",
                        timerSecondsRemaining: session.state.timerSecondsRemaining,
                        currentInterval: 1,
                        updatedAt: Date()
                    )
                    FirestoreClient.shared.updateSessionState(roomId: session.roomId, state: startState, template: template) { _ in }
                case .failure(let error):
                    self.joinError = "Failed to create session: \(error.localizedDescription)"
                }
            }
        }
    }
    
    func startSoloSession() {
        isSoloMode = true
        isLeader = true
        roomId = ""
        participants = []
        timerSecondsRemaining = focusTime * 60
        totalSeconds = focusTime * 60
        status = "running"
        currentPhase = "focus"
        currentInterval = 1
        cycleCompleted = false
        lastPhase = "focus"
        
        step = "active-session"
        startBackgroundTasks()
    }
    
    // 2. Join Room Gate Verification
    func prepareToJoin() {
        let code = inputRoomId.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard !code.isEmpty else { return }
        
        // Extract room from full URL if pasted
        var finalCode = code
        if code.contains("?ROOM=") {
            if let extracted = code.components(separatedBy: "?ROOM=").last?.components(separatedBy: "&").first {
                finalCode = extracted.uppercased()
            }
        } else if code.contains("?room=") {
            if let extracted = code.components(separatedBy: "?room=").last?.components(separatedBy: "&").first {
                finalCode = extracted.uppercased()
            }
        }
        
        roomId = finalCode
        
        if userName.isEmpty {
            step = "join-room-gate"
        } else {
            joinRoom()
        }
    }
    
    func joinRoom() {
        guard !roomId.isEmpty else { return }
        isSubmitting = true
        joinError = ""
        
        saveUserName(userName)
        
        // Check if room exists
        FirestoreClient.shared.fetchSession(roomId: roomId) { [weak self] result in
            DispatchQueue.main.async {
                guard let self = self else { return }
                
                switch result {
                case .success(let session):
                    // Room exists, join it
                    let isLeaderCheck = session.leaderId == self.userId
                    self.isLeader = isLeaderCheck
                    
                    FirestoreClient.shared.joinSession(roomId: self.roomId, userId: self.userId, name: self.userName, role: isLeaderCheck ? "leader" : "participant") { [weak self] joinResult in
                        DispatchQueue.main.async {
                            guard let self = self else { return }
                            self.isSubmitting = false
                            
                            switch joinResult {
                            case .success:
                                self.timerSecondsRemaining = session.state.timerSecondsRemaining
                                self.status = session.state.status
                                self.currentPhase = session.state.currentPhase
                                self.currentInterval = session.state.currentInterval
                                self.lastPhase = session.state.currentPhase
                                self.totalIntervals = session.template.longBreakInterval
                                self.focusTime = session.template.focusTime / 60
                                self.shortBreak = session.template.shortBreakTime / 60
                                self.longBreak = session.template.longBreakTime / 60
                                self.cycleCompleted = false
                                
                                self.step = "active-session"
                                self.startBackgroundTasks()
                            case .failure(let error):
                                self.joinError = "Failed to join room: \(error.localizedDescription)"
                                self.step = "home"
                            }
                        }
                    }
                case .failure(let error):
                    self.isSubmitting = false
                    self.joinError = "Study room not found: \(error.localizedDescription)"
                    self.step = "home"
                }
            }
        }
    }
    
    private func joinParticipantSubcollection() {
        FirestoreClient.shared.joinSession(roomId: roomId, userId: userId, name: userName, role: isLeader ? "leader" : "participant") { _ in }
    }
    
    // 3. Polling & Syncing Loop
    func startBackgroundTasks() {
        stopBackgroundTasks()
        
        // A. Local countdown clock (every 1 second)
        countdownTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            if self.status == "running" {
                if self.timerSecondsRemaining > 0 {
                    self.timerSecondsRemaining -= 1
                } else if self.isLeader {
                    self.handleTimerComplete()
                }
            }
        }
        
        // Skip database syncing/polling if in solo mode
        if isSoloMode { return }
        
        // B. Database state poll (every 1.5 seconds)
        dbPollTimer = Timer.scheduledTimer(withTimeInterval: 1.5, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            FirestoreClient.shared.fetchSession(roomId: self.roomId) { result in
                DispatchQueue.main.async {
                    switch result {
                    case .success(let session):
                        // If user opened settings, don't overwrite user changes
                        if !self.showSettings {
                            self.focusTime = session.template.focusTime / 60
                            self.shortBreak = session.template.shortBreakTime / 60
                            self.longBreak = session.template.longBreakTime / 60
                            self.totalIntervals = session.template.longBreakInterval
                        }
                        
                        let remoteState = session.state
                        self.status = remoteState.status
                        self.currentInterval = remoteState.currentInterval
                        
                        // Handle phase change sounds
                        if self.currentPhase != remoteState.currentPhase {
                            self.currentPhase = remoteState.currentPhase
                            self.playChime(for: remoteState.currentPhase)
                        }
                        
                        // If remote timer is running, calculate offset since last update
                        if remoteState.status == "running" {
                            let elapsed = Int(Date().timeIntervalSince(remoteState.updatedAt))
                            let remaining = max(0, remoteState.timerSecondsRemaining - elapsed)
                            self.timerSecondsRemaining = remaining
                        } else {
                            self.timerSecondsRemaining = remoteState.timerSecondsRemaining
                        }
                        
                        // Sync total seconds for dialysis
                        switch remoteState.currentPhase {
                        case "shortBreak": self.totalSeconds = session.template.shortBreakTime
                        case "longBreak": self.totalSeconds = session.template.longBreakTime
                        default: self.totalSeconds = session.template.focusTime
                        }
                    case .failure:
                        // Document deleted by leader
                        if !self.isLeader {
                            self.leaveRoom()
                        }
                    }
                }
            }
        }
        
        // C. Participants poll (every 5 seconds)
        participantsPollTimer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            FirestoreClient.shared.fetchParticipants(roomId: self.roomId) { result in
                DispatchQueue.main.async {
                    if case .success(let list) = result {
                        let now = Date()
                        // Filter active participants (last active < 120s)
                        let active = list.filter { now.timeIntervalSince($0.lastActive) < 120 }
                        // Sort: leader first
                        self.participants = active.sorted { a, b in
                            b.role.compare(a.role) == .orderedAscending
                        }
                    }
                }
            }
        }
        
        // D. Heartbeat updates (every 30 seconds)
        heartbeatTimer = Timer.scheduledTimer(withTimeInterval: 30.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            FirestoreClient.shared.updateHeartbeat(roomId: self.roomId, userId: self.userId) { _ in }
        }
    }
    
    func stopBackgroundTasks() {
        countdownTimer?.invalidate()
        dbPollTimer?.invalidate()
        participantsPollTimer?.invalidate()
        heartbeatTimer?.invalidate()
        
        countdownTimer = nil
        dbPollTimer = nil
        participantsPollTimer = nil
        heartbeatTimer = nil
    }
    
    // Play transition alert
    private func playChime(for phase: String) {
        guard soundEnabled else { return }
        switch phase {
        case "shortBreak":
            AudioSynth.shared.playShortBreakAlert()
        case "longBreak":
            AudioSynth.shared.playLongBreakAlert()
        default:
            AudioSynth.shared.playFocusAlert()
        }
    }
    
    // 4. Timer Controls (Leader only)
    func toggleTimer() {
        guard isLeader else { return }
        let newStatus = status == "running" ? "paused" : "running"
        status = newStatus
        
        if isSoloMode { return }
        
        let state = SessionState(
            status: newStatus,
            currentPhase: currentPhase,
            timerSecondsRemaining: timerSecondsRemaining,
            currentInterval: currentInterval,
            updatedAt: Date()
        )
        
        FirestoreClient.shared.updateSessionState(roomId: roomId, state: state) { _ in }
    }
    
    func resetTimer() {
        guard isLeader else { return }
        status = "idle"
        
        let seconds: Int
        switch currentPhase {
        case "shortBreak": seconds = shortBreak * 60
        case "longBreak": seconds = longBreak * 60
        default: seconds = focusTime * 60
        }
        
        timerSecondsRemaining = seconds
        
        if isSoloMode { return }
        
        let state = SessionState(
            status: "idle",
            currentPhase: currentPhase,
            timerSecondsRemaining: seconds,
            currentInterval: currentInterval,
            updatedAt: Date()
        )
        
        FirestoreClient.shared.updateSessionState(roomId: roomId, state: state) { _ in }
    }
    
    func skipPhase() {
        guard isLeader else { return }
        transitionPhase(isSkipped: true)
    }
    
    private func transitionPhase(isSkipped: Bool) {
        var nextPhase = "focus"
        var nextSeconds = focusTime * 60
        var nextInterval = currentInterval
        
        if currentPhase == "focus" {
            if currentInterval >= totalIntervals {
                nextPhase = "longBreak"
                nextSeconds = longBreak * 60
                nextInterval = 1
            } else {
                nextPhase = "shortBreak"
                nextSeconds = shortBreak * 60
                nextInterval = currentInterval + 1
            }
        } else {
            nextPhase = "focus"
            nextSeconds = focusTime * 60
        }
        
        status = "running"
        currentPhase = nextPhase
        currentInterval = nextInterval
        timerSecondsRemaining = nextSeconds
        
        // Play local transition sound for leader
        playChime(for: nextPhase)
        
        if isSoloMode { return }
        
        let state = SessionState(
            status: "running",
            currentPhase: nextPhase,
            timerSecondsRemaining: nextSeconds,
            currentInterval: nextInterval,
            updatedAt: Date()
        )
        
        FirestoreClient.shared.updateSessionState(roomId: roomId, state: state) { _ in }
    }
    
    private func handleTimerComplete() {
        if currentPhase == "longBreak" {
            status = "paused"
            timerSecondsRemaining = 0
            cycleCompleted = true
            
            // Play local transition sound for leader
            playChime(for: "longBreak")
            
            if isSoloMode { return }
            
            let state = SessionState(
                status: "paused",
                currentPhase: "longBreak",
                timerSecondsRemaining: 0,
                currentInterval: 1,
                updatedAt: Date()
            )
            FirestoreClient.shared.updateSessionState(roomId: roomId, state: state) { _ in }
        } else {
            transitionPhase(isSkipped: false)
        }
    }
    
    func continueCycle() {
        guard isLeader else { return }
        cycleCompleted = false
        
        let state = SessionState(
            status: "running",
            currentPhase: "focus",
            timerSecondsRemaining: focusTime * 60,
            currentInterval: 1,
            updatedAt: Date()
        )
        
        FirestoreClient.shared.updateSessionState(roomId: roomId, state: state) { _ in }
    }
    
    // Save Settings Setup
    func saveSettingsInDb() {
        guard isLeader else { return }
        showSettings = false
        
        let seconds = focusTime * 60
        timerSecondsRemaining = seconds
        totalSeconds = seconds
        status = "running"
        currentPhase = "focus"
        currentInterval = 1
        
        if isSoloMode { return }
        
        let template = SessionTemplate(
            focusTime: focusTime * 60,
            shortBreakTime: shortBreak * 60,
            longBreakTime: longBreak * 60,
            longBreakInterval: totalIntervals
        )
        
        let state = SessionState(
            status: "running",
            currentPhase: "focus",
            timerSecondsRemaining: seconds,
            currentInterval: 1,
            updatedAt: Date()
        )
        
        FirestoreClient.shared.updateSessionState(roomId: roomId, state: state, template: template) { _ in }
    }
    
    // 5. Leave Room Actions
    func leaveRoom() {
        stopBackgroundTasks()
        
        if isSoloMode {
            DispatchQueue.main.async {
                self.roomId = ""
                self.isLeader = false
                self.isSoloMode = false
                self.status = "idle"
                self.step = "home"
                self.participants = []
                self.cycleCompleted = false
            }
            return
        }
        
        let rid = roomId
        let uid = userId
        
        // Fire and forget deletion
        FirestoreClient.shared.leaveSession(roomId: rid, userId: uid) { _ in }
        
        // If leader and abandoning, delete session document
        if isLeader {
            // Note: firestore rules allow delete, but let's let client delete root too
            let url = URL(string: "https://firestore.googleapis.com/v1/projects/shutupnstudy-1734a/databases/(default)/documents/sessions/\(rid)")!
            var req = URLRequest(url: url)
            req.httpMethod = "DELETE"
            URLSession.shared.dataTask(with: req).resume()
        }
        
        DispatchQueue.main.async {
            self.roomId = ""
            self.isLeader = false
            self.status = "idle"
            self.step = "home"
            self.participants = []
            self.cycleCompleted = false
        }
    }
}
