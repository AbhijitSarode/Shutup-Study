import Foundation
import AVFoundation

class AudioSynth {
    static let shared = AudioSynth()
    
    private let audioEngine = AVAudioEngine()
    private var mixer: AVAudioMixerNode {
        return audioEngine.mainMixerNode
    }
    
    init() {
        // Leave empty to prevent crashes on startup due to an empty audio graph
    }
    
    func playTone(frequency: Double, duration: Double, type: String = "sine") {
        var currentPhase: Float = 0.0
        
        var sampleRate = mixer.outputFormat(forBus: 0).sampleRate
        if sampleRate <= 0 {
            sampleRate = 44100.0
        }
        
        let phaseIncrement = Float(2.0 * .pi * frequency / sampleRate)
        
        let node = AVAudioSourceNode { (_, _, frameCount, audioBufferList) -> OSStatus in
            let abl = UnsafeMutableAudioBufferListPointer(audioBufferList)
            guard let firstBuffer = abl.first,
                  let ptr = firstBuffer.mData?.assumingMemoryBound(to: Float.self) else {
                return noErr
            }
            
            for frame in 0..<Int(frameCount) {
                let val: Float
                if type == "triangle" {
                    let x = currentPhase / Float(2.0 * .pi)
                    val = 4.0 * abs(x - floor(x + 0.5)) - 1.0
                } else {
                    val = sin(currentPhase)
                }
                
                ptr[frame] = val * 0.12
                
                currentPhase += phaseIncrement
                if currentPhase >= Float(2.0 * .pi) {
                    currentPhase -= Float(2.0 * .pi)
                }
            }
            
            // If output format is multi-channel, copy mono output channel to other channels
            if abl.count > 1 {
                for i in 1..<abl.count {
                    if let destPtr = abl[i].mData?.assumingMemoryBound(to: Float.self) {
                        for frame in 0..<Int(frameCount) {
                            destPtr[frame] = ptr[frame]
                        }
                    }
                }
            }
            
            return noErr
        }
        
        audioEngine.attach(node)
        let format = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 1)!
        audioEngine.connect(node, to: mixer, format: format)
        
        if !audioEngine.isRunning {
            do {
                try audioEngine.start()
            } catch {
                print("Failed to start AVAudioEngine: \(error)")
            }
        }
        
        // Disconnect and detach after the duration ends
        DispatchQueue.main.asyncAfter(deadline: .now() + duration) { [weak self] in
            guard let self = self else { return }
            self.audioEngine.detach(node)
        }
    }
    
    func playFocusAlert() {
        let frequencies = [523.25, 659.25, 783.99, 1046.50] // C5, E5, G5, C6
        for (idx, freq) in frequencies.enumerated() {
            DispatchQueue.main.asyncAfter(deadline: .now() + Double(idx) * 0.12) {
                self.playTone(frequency: freq, duration: 0.35, type: "sine")
            }
        }
    }
    
    func playShortBreakAlert() {
        let frequencies = [587.33, 440.00] // D5, A4
        for (idx, freq) in frequencies.enumerated() {
            DispatchQueue.main.asyncAfter(deadline: .now() + Double(idx) * 0.18) {
                self.playTone(frequency: freq, duration: 0.45, type: "triangle")
            }
        }
    }
    
    func playLongBreakAlert() {
        let frequencies = [349.23, 523.25, 698.46, 880.00] // F4, C5, F5, A5
        for (idx, freq) in frequencies.enumerated() {
            DispatchQueue.main.asyncAfter(deadline: .now() + Double(idx) * 0.10) {
                self.playTone(frequency: freq, duration: 0.45, type: "sine")
            }
        }
    }
    
    func playClickSound() {
        playTone(frequency: 440.00, duration: 0.08, type: "triangle")
    }
}
