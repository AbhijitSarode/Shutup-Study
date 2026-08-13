import Foundation

struct FirestoreValue {
    static func string(_ dict: [String: Any]?) -> String? {
        guard let d = dict else { return nil }
        return d["stringValue"] as? String
    }
    
    static func int(_ dict: [String: Any]?) -> Int? {
        guard let d = dict else { return nil }
        if let str = d["integerValue"] as? String {
            return Int(str)
        }
        if let num = d["integerValue"] as? Int {
            return num
        }
        return nil
    }
    
    static func map(_ dict: [String: Any]?) -> [String: Any]? {
        guard let d = dict else { return nil }
        if let mapVal = d["mapValue"] as? [String: Any] {
            return mapVal["fields"] as? [String: Any]
        }
        return nil
    }
    
    static func timestamp(_ dict: [String: Any]?) -> Date? {
        guard let d = dict, let str = d["timestampValue"] as? String else { return nil }
        
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: str) {
            return date
        }
        
        let formatter2 = ISO8601DateFormatter()
        formatter2.formatOptions = [.withInternetDateTime]
        return formatter2.date(from: str)
    }
    
    static func formatTimestamp(_ date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.string(from: date)
    }
}

struct SessionTemplate: Equatable {
    var focusTime: Int // in seconds
    var shortBreakTime: Int
    var longBreakTime: Int
    var longBreakInterval: Int
    
    static func from(fields: [String: Any]) -> SessionTemplate {
        let focus = FirestoreValue.int(fields["focusTime"] as? [String: Any]) ?? 1500
        let sBreak = FirestoreValue.int(fields["shortBreakTime"] as? [String: Any]) ?? 300
        let lBreak = FirestoreValue.int(fields["longBreakTime"] as? [String: Any]) ?? 900
        let intervals = FirestoreValue.int(fields["longBreakInterval"] as? [String: Any]) ?? 4
        return SessionTemplate(focusTime: focus, shortBreakTime: sBreak, longBreakTime: lBreak, longBreakInterval: intervals)
    }
}

struct SessionState: Equatable {
    var status: String // "idle" | "running" | "paused"
    var currentPhase: String // "focus" | "shortBreak" | "longBreak"
    var timerSecondsRemaining: Int
    var currentInterval: Int
    var updatedAt: Date
    
    static func from(fields: [String: Any]) -> SessionState {
        let status = FirestoreValue.string(fields["status"] as? [String: Any]) ?? "idle"
        let currentPhase = FirestoreValue.string(fields["currentPhase"] as? [String: Any]) ?? "focus"
        let timerSecondsRemaining = FirestoreValue.int(fields["timerSecondsRemaining"] as? [String: Any]) ?? 1500
        let currentInterval = FirestoreValue.int(fields["currentInterval"] as? [String: Any]) ?? 1
        let updatedAt = FirestoreValue.timestamp(fields["updatedAt"] as? [String: Any]) ?? Date()
        return SessionState(status: status, currentPhase: currentPhase, timerSecondsRemaining: timerSecondsRemaining, currentInterval: currentInterval, updatedAt: updatedAt)
    }
}

struct SessionData: Equatable {
    var roomId: String
    var leaderId: String
    var leaderName: String
    var template: SessionTemplate
    var state: SessionState
    
    static func from(document: [String: Any]) -> SessionData? {
        guard let name = document["name"] as? String,
              let fields = document["fields"] as? [String: Any] else {
            return nil
        }
        
        let roomId = name.components(separatedBy: "/").last ?? ""
        let leaderId = FirestoreValue.string(fields["leaderId"] as? [String: Any]) ?? ""
        let leaderName = FirestoreValue.string(fields["leaderName"] as? [String: Any]) ?? ""
        
        let templateFields = FirestoreValue.map(fields["template"] as? [String: Any]) ?? [:]
        let template = SessionTemplate.from(fields: templateFields)
        
        let stateFields = FirestoreValue.map(fields["state"] as? [String: Any]) ?? [:]
        let state = SessionState.from(fields: stateFields)
        
        return SessionData(roomId: roomId, leaderId: leaderId, leaderName: leaderName, template: template, state: state)
    }
}

struct Participant: Identifiable, Equatable {
    var id: String // userId
    var name: String
    var role: String // "leader" | "participant"
    var joinedAt: Date
    var lastActive: Date
    
    static func from(document: [String: Any]) -> Participant? {
        guard let namePath = document["name"] as? String,
              let fields = document["fields"] as? [String: Any] else {
            return nil
        }
        let id = namePath.components(separatedBy: "/").last ?? ""
        let name = FirestoreValue.string(fields["name"] as? [String: Any]) ?? ""
        let role = FirestoreValue.string(fields["role"] as? [String: Any]) ?? "participant"
        let joinedAt = FirestoreValue.timestamp(fields["joinedAt"] as? [String: Any]) ?? Date()
        let lastActive = FirestoreValue.timestamp(fields["lastActive"] as? [String: Any]) ?? Date()
        return Participant(id: id, name: name, role: role, joinedAt: joinedAt, lastActive: lastActive)
    }
}
