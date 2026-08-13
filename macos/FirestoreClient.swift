import Foundation

class FirestoreClient {
    static let shared = FirestoreClient()
    
    private let projectId = "shutupnstudy-1734a"
    private var baseURL: String {
        return "https://firestore.googleapis.com/v1/projects/\(projectId)/databases/(default)/documents"
    }
    
    // Helpers to serialize templates and state
    private func serialize(template: SessionTemplate) -> [String: Any] {
        return [
            "mapValue": [
                "fields": [
                    "focusTime": ["integerValue": "\(template.focusTime)"],
                    "shortBreakTime": ["integerValue": "\(template.shortBreakTime)"],
                    "longBreakTime": ["integerValue": "\(template.longBreakTime)"],
                    "longBreakInterval": ["integerValue": "\(template.longBreakInterval)"]
                ]
            ]
        ]
    }
    
    private func serialize(state: SessionState) -> [String: Any] {
        return [
            "mapValue": [
                "fields": [
                    "status": ["stringValue": state.status],
                    "currentPhase": ["stringValue": state.currentPhase],
                    "timerSecondsRemaining": ["integerValue": "\(state.timerSecondsRemaining)"],
                    "currentInterval": ["integerValue": "\(state.currentInterval)"],
                    "updatedAt": ["timestampValue": FirestoreValue.formatTimestamp(state.updatedAt)]
                ]
            ]
        ]
    }
    
    // 1. Fetch Session
    func fetchSession(roomId: String, completion: @escaping (Result<SessionData, Error>) -> Void) {
        let urlString = "\(baseURL)/sessions/\(roomId.uppercased())"
        guard let url = URL(string: urlString) else {
            completion(.failure(NSError(domain: NSCocoaErrorDomain, code: NSURLErrorBadURL, userInfo: nil)))
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            
            guard let httpResponse = response as? HTTPURLResponse else {
                completion(.failure(NSError(domain: "FirestoreClient", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid response"])))
                return
            }
            
            if httpResponse.statusCode != 200 {
                completion(.failure(NSError(domain: "FirestoreClient", code: httpResponse.statusCode, userInfo: [NSLocalizedDescriptionKey: "Document not found or network error (Code \(httpResponse.statusCode))"])))
                return
            }
            
            guard let data = data else {
                completion(.failure(NSError(domain: "FirestoreClient", code: -2, userInfo: [NSLocalizedDescriptionKey: "No data received"])))
                return
            }
            
            do {
                if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let session = SessionData.from(document: json) {
                    completion(.success(session))
                } else {
                    completion(.failure(NSError(domain: "FirestoreClient", code: -3, userInfo: [NSLocalizedDescriptionKey: "Failed to parse session document"])))
                }
            } catch {
                completion(.failure(error))
            }
        }.resume()
    }
    
    // 2. Create Session
    func createSession(roomId: String, leaderId: String, leaderName: String, template: SessionTemplate, completion: @escaping (Result<SessionData, Error>) -> Void) {
        let urlString = "\(baseURL)/sessions?documentId=\(roomId.uppercased())"
        guard let url = URL(string: urlString) else {
            completion(.failure(NSError(domain: NSCocoaErrorDomain, code: NSURLErrorBadURL, userInfo: nil)))
            return
        }
        
        let initialFields: [String: Any] = [
            "leaderId": ["stringValue": leaderId],
            "leaderName": ["stringValue": leaderName],
            "template": serialize(template: template),
            "state": serialize(state: SessionState(status: "idle", currentPhase: "focus", timerSecondsRemaining: template.focusTime, currentInterval: 1, updatedAt: Date()))
        ]
        
        let payload: [String: Any] = ["fields": initialFields]
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        } catch {
            completion(.failure(error))
            return
        }
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            
            guard let httpResponse = response as? HTTPURLResponse else {
                completion(.failure(NSError(domain: "FirestoreClient", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid response"])))
                return
            }
            
            if httpResponse.statusCode != 200 && httpResponse.statusCode != 201 {
                completion(.failure(NSError(domain: "FirestoreClient", code: httpResponse.statusCode, userInfo: [NSLocalizedDescriptionKey: "Error creating session (Code \(httpResponse.statusCode))"])))
                return
            }
            
            guard let data = data else {
                completion(.failure(NSError(domain: "FirestoreClient", code: -2, userInfo: [NSLocalizedDescriptionKey: "No data received"])))
                return
            }
            
            do {
                if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let session = SessionData.from(document: json) {
                    completion(.success(session))
                } else {
                    completion(.failure(NSError(domain: "FirestoreClient", code: -3, userInfo: [NSLocalizedDescriptionKey: "Failed to parse created session"])))
                }
            } catch {
                completion(.failure(error))
            }
        }.resume()
    }
    
    // 3. Update Session State (only patches the state and template fields)
    func updateSessionState(roomId: String, state: SessionState, template: SessionTemplate? = nil, completion: @escaping (Result<Void, Error>) -> Void) {
        var queryItems = "?updateMask.fieldPaths=state"
        var stateFields: [String: Any] = [
            "state": serialize(state: state)
        ]
        
        if let template = template {
            queryItems += "&updateMask.fieldPaths=template"
            stateFields["template"] = serialize(template: template)
        }
        
        let urlString = "\(baseURL)/sessions/\(roomId.uppercased())\(queryItems)"
        guard let url = URL(string: urlString) else {
            completion(.failure(NSError(domain: NSCocoaErrorDomain, code: NSURLErrorBadURL, userInfo: nil)))
            return
        }
        
        let payload: [String: Any] = ["fields": stateFields]
        
        var request = URLRequest(url: url)
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        } catch {
            completion(.failure(error))
            return
        }
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            
            guard let httpResponse = response as? HTTPURLResponse else {
                completion(.failure(NSError(domain: "FirestoreClient", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid response"])))
                return
            }
            
            if httpResponse.statusCode == 200 {
                completion(.success(()))
            } else {
                completion(.failure(NSError(domain: "FirestoreClient", code: httpResponse.statusCode, userInfo: [NSLocalizedDescriptionKey: "Failed to update state (Code \(httpResponse.statusCode))"])))
            }
        }.resume()
    }
    
    // 4. Join Session / Upsert Participant
    func joinSession(roomId: String, userId: String, name: String, role: String, completion: @escaping (Result<Void, Error>) -> Void) {
        let urlString = "\(baseURL)/sessions/\(roomId.uppercased())/participants/\(userId)"
        guard let url = URL(string: urlString) else {
            completion(.failure(NSError(domain: NSCocoaErrorDomain, code: NSURLErrorBadURL, userInfo: nil)))
            return
        }
        
        let payload: [String: Any] = [
            "fields": [
                "name": ["stringValue": name],
                "role": ["stringValue": role],
                "joinedAt": ["timestampValue": FirestoreValue.formatTimestamp(Date())],
                "lastActive": ["timestampValue": FirestoreValue.formatTimestamp(Date())]
            ]
        ]
        
        var request = URLRequest(url: url)
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        } catch {
            completion(.failure(error))
            return
        }
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            
            guard let httpResponse = response as? HTTPURLResponse else {
                completion(.failure(NSError(domain: "FirestoreClient", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid response"])))
                return
            }
            
            if httpResponse.statusCode == 200 {
                completion(.success(()))
            } else {
                completion(.failure(NSError(domain: "FirestoreClient", code: httpResponse.statusCode, userInfo: [NSLocalizedDescriptionKey: "Failed to join room (Code \(httpResponse.statusCode))"])))
            }
        }.resume()
    }
    
    // 5. Update Heartbeat
    func updateHeartbeat(roomId: String, userId: String, completion: @escaping (Result<Void, Error>) -> Void) {
        let urlString = "\(baseURL)/sessions/\(roomId.uppercased())/participants/\(userId)?updateMask.fieldPaths=lastActive"
        guard let url = URL(string: urlString) else {
            completion(.failure(NSError(domain: NSCocoaErrorDomain, code: NSURLErrorBadURL, userInfo: nil)))
            return
        }
        
        let payload: [String: Any] = [
            "fields": [
                "lastActive": ["timestampValue": FirestoreValue.formatTimestamp(Date())]
            ]
        ]
        
        var request = URLRequest(url: url)
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        } catch {
            completion(.failure(error))
            return
        }
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            
            guard let httpResponse = response as? HTTPURLResponse else {
                completion(.failure(NSError(domain: "FirestoreClient", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid response"])))
                return
            }
            
            if httpResponse.statusCode == 200 {
                completion(.success(()))
            } else {
                completion(.failure(NSError(domain: "FirestoreClient", code: httpResponse.statusCode, userInfo: [NSLocalizedDescriptionKey: "Heartbeat sync failed (Code \(httpResponse.statusCode))"])))
            }
        }.resume()
    }
    
    // 6. Fetch Participants
    func fetchParticipants(roomId: String, completion: @escaping (Result<[Participant], Error>) -> Void) {
        let urlString = "\(baseURL)/sessions/\(roomId.uppercased())/participants"
        guard let url = URL(string: urlString) else {
            completion(.failure(NSError(domain: NSCocoaErrorDomain, code: NSURLErrorBadURL, userInfo: nil)))
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            
            guard let httpResponse = response as? HTTPURLResponse else {
                completion(.failure(NSError(domain: "FirestoreClient", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid response"])))
                return
            }
            
            // If the collections has zero participants, Firestore might return 404 or empty list.
            if httpResponse.statusCode == 404 {
                completion(.success([]))
                return
            }
            
            if httpResponse.statusCode != 200 {
                completion(.failure(NSError(domain: "FirestoreClient", code: httpResponse.statusCode, userInfo: [NSLocalizedDescriptionKey: "Error fetching participants (Code \(httpResponse.statusCode))"])))
                return
            }
            
            guard let data = data else {
                completion(.success([]))
                return
            }
            
            do {
                if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let documents = json["documents"] as? [[String: Any]] {
                    
                    let list = documents.compactMap { Participant.from(document: $0) }
                    completion(.success(list))
                } else {
                    // Try parsing as empty or single document format
                    if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any], json.isEmpty {
                        completion(.success([]))
                    } else {
                        completion(.success([]))
                    }
                }
            } catch {
                completion(.failure(error))
            }
        }.resume()
    }
    
    // 7. Leave Session / Remove Participant
    func leaveSession(roomId: String, userId: String, completion: @escaping (Result<Void, Error>) -> Void) {
        let urlString = "\(baseURL)/sessions/\(roomId.uppercased())/participants/\(userId)"
        guard let url = URL(string: urlString) else {
            completion(.failure(NSError(domain: NSCocoaErrorDomain, code: NSURLErrorBadURL, userInfo: nil)))
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            
            guard let httpResponse = response as? HTTPURLResponse else {
                completion(.failure(NSError(domain: "FirestoreClient", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid response"])))
                return
            }
            
            if httpResponse.statusCode == 200 || httpResponse.statusCode == 204 {
                completion(.success(()))
            } else {
                completion(.failure(NSError(domain: "FirestoreClient", code: httpResponse.statusCode, userInfo: [NSLocalizedDescriptionKey: "Failed to leave session (Code \(httpResponse.statusCode))"])))
            }
        }.resume()
    }
}
