import React, { useState, useEffect, useRef } from 'react';
import { 
  Play, Pause, RotateCcw, SkipForward, Users, Settings, 
  LogOut, Copy, Check, Shield, User, Volume2, VolumeX, Edit3
} from 'lucide-react';
import { 
  doc, onSnapshot, updateDoc, setDoc, 
  collection, deleteDoc
} from 'firebase/firestore';
import { db, serverTimestamp } from '../firebase';
import confetti from 'canvas-confetti';

export default function StudySession({ roomId, userId, userName, isLeader, onLeave }) {
  const [session, setSession] = useState(null);
  const [participants, setParticipants] = useState([]);
  const [localSecondsRemaining, setLocalSecondsRemaining] = useState(1500);
  const [copied, setCopied] = useState(false);
  const [soundEnabled, setSoundEnabled] = useState(true);
  const [showSettings, setShowSettings] = useState(false);
  
  // Settings Form State
  const [focusInput, setFocusInput] = useState(25);
  const [shortBreakInput, setShortBreakInput] = useState(5);
  const [longBreakInput, setLongBreakInput] = useState(15);
  const [intervalsInput, setIntervalsInput] = useState(4);
  
  const timerIntervalRef = useRef(null);
  const heartbeatIntervalRef = useRef(null);
  const leaderHeartbeatRef = useRef(null);
  const prevPhaseRef = useRef(null);
  const audioContextRef = useRef(null);

  const initAudioContext = () => {
    try {
      if (!audioContextRef.current) {
        audioContextRef.current = new (window.AudioContext || window.webkitAudioContext)();
      }
      if (audioContextRef.current && audioContextRef.current.state === 'suspended') {
        audioContextRef.current.resume();
      }
      return audioContextRef.current;
    } catch (e) {
      console.warn("Failed to initialize AudioContext:", e);
      return null;
    }
  };

  // Audio elements (built-in browser synth using Web Audio API to avoid external asset dependency!)
  const playAlertSound = (type) => {
    if (!soundEnabled) return;
    try {
      const audioCtx = initAudioContext();
      if (!audioCtx) return;
      const now = audioCtx.currentTime;
      
      if (type === 'focus') {
        // Bright, motivating ascending chime
        const frequencies = [523.25, 659.25, 783.99, 1046.50]; // C5, E5, G5, C6
        frequencies.forEach((freq, idx) => {
          const osc = audioCtx.createOscillator();
          const gain = audioCtx.createGain();
          
          osc.type = 'sine';
          osc.frequency.setValueAtTime(freq, now + idx * 0.12);
          
          gain.gain.setValueAtTime(0, now);
          gain.gain.linearRampToValueAtTime(0.12, now + idx * 0.12 + 0.02);
          gain.gain.exponentialRampToValueAtTime(0.001, now + idx * 0.12 + 0.35);
          
          osc.connect(gain);
          gain.connect(audioCtx.destination);
          
          osc.start(now + idx * 0.12);
          osc.stop(now + idx * 0.12 + 0.4);
        });
      } else if (type === 'shortBreak') {
        // Soothing, calm descending tone (D5 to A4)
        const frequencies = [587.33, 440.00]; // D5, A4
        frequencies.forEach((freq, idx) => {
          const osc = audioCtx.createOscillator();
          const gain = audioCtx.createGain();
          
          osc.type = 'triangle';
          osc.frequency.setValueAtTime(freq, now + idx * 0.18);
          
          gain.gain.setValueAtTime(0, now);
          gain.gain.linearRampToValueAtTime(0.12, now + idx * 0.18 + 0.02);
          gain.gain.exponentialRampToValueAtTime(0.001, now + idx * 0.18 + 0.45);
          
          osc.connect(gain);
          gain.connect(audioCtx.destination);
          
          osc.start(now + idx * 0.18);
          osc.stop(now + idx * 0.18 + 0.5);
        });
      } else if (type === 'longBreak') {
        // Uplifting arpeggio (F4, C5, F5, A5)
        const frequencies = [349.23, 523.25, 698.46, 880.00]; // F4, C5, F5, A5
        frequencies.forEach((freq, idx) => {
          const osc = audioCtx.createOscillator();
          const gain = audioCtx.createGain();
          
          osc.type = 'sine';
          osc.frequency.setValueAtTime(freq, now + idx * 0.1);
          
          gain.gain.setValueAtTime(0, now);
          gain.gain.linearRampToValueAtTime(0.12, now + idx * 0.1 + 0.02);
          gain.gain.exponentialRampToValueAtTime(0.001, now + idx * 0.1 + 0.45);
          
          osc.connect(gain);
          gain.connect(audioCtx.destination);
          
          osc.start(now + idx * 0.1);
          osc.stop(now + idx * 0.1 + 0.5);
        });
      } else {
        // Soft button click chirp
        const osc = audioCtx.createOscillator();
        const gain = audioCtx.createGain();
        
        osc.type = 'triangle';
        osc.frequency.setValueAtTime(440, now); // A4
        gain.gain.setValueAtTime(0.08, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.12);
        
        osc.connect(gain);
        gain.connect(audioCtx.destination);
        
        osc.start(now);
        osc.stop(now + 0.12);
      }
    } catch (e) {
      console.warn("Audio Context failed to start:", e);
    }
  };

  // 1. Subscribe to Session Document
  useEffect(() => {
    if (!roomId) return;
    const sessionDocRef = doc(db, 'sessions', roomId);
    
    const unsubscribe = onSnapshot(sessionDocRef, 
      (snapshot) => {
        if (snapshot.exists()) {
          const data = snapshot.data();
          setSession(data);
          
          // Sync templates into inputs if settings modal is closed
          if (!showSettings) {
            setFocusInput(data.template.focusTime / 60);
            setShortBreakInput(data.template.shortBreakTime / 60);
            setLongBreakInput(data.template.longBreakTime / 60);
            setIntervalsInput(data.template.longBreakInterval);
          }

          // Timer Sync Logic
          const state = data.state;
          const now = Date.now();
          
          if (state.status === 'running' && state.updatedAt) {
            const updatedAtMillis = state.updatedAt.toDate ? state.updatedAt.toDate().getTime() : now;
            const elapsedSeconds = Math.max(0, Math.floor((now - updatedAtMillis) / 1000));
            const calculatedRemaining = Math.max(0, state.timerSecondsRemaining - elapsedSeconds);
            
            setLocalSecondsRemaining(calculatedRemaining);
          } else {
            setLocalSecondsRemaining(state.timerSecondsRemaining);
          }
        } else {
          console.warn("Session does not exist");
          onLeave();
        }
      },
      (error) => {
        console.warn("Session document listener error (likely deleted):", error);
        onLeave();
      }
    );

    return () => unsubscribe();
  }, [roomId, onLeave, showSettings]);

  // 2. Subscribe to Participants List
  useEffect(() => {
    if (!roomId) return;
    const participantsRef = collection(db, 'sessions', roomId, 'participants');
    
    const unsubscribe = onSnapshot(participantsRef, 
      (snapshot) => {
        const activeList = [];
        const now = Date.now();
        snapshot.forEach((doc) => {
          const p = doc.data();
          p.id = doc.id;
          // Filter out inactive participants (no heartbeat in 2 minutes)
          const lastActiveMillis = p.lastActive?.toDate ? p.lastActive.toDate().getTime() : now;
          if (now - lastActiveMillis < 120000) {
            activeList.push(p);
          }
        });
        setParticipants(activeList.sort((a, b) => b.role.localeCompare(a.role)));
      },
      (error) => {
        console.warn("Participants list listener error (likely deleted):", error);
      }
    );

    // Write join status & set up heartbeat
    const participantDocRef = doc(db, 'sessions', roomId, 'participants', userId);
    setDoc(participantDocRef, {
      name: userName,
      role: isLeader ? 'leader' : 'participant',
      joinedAt: serverTimestamp(),
      lastActive: serverTimestamp()
    }, { merge: true });

    heartbeatIntervalRef.current = setInterval(() => {
      updateDoc(participantDocRef, {
        lastActive: serverTimestamp()
      }).catch(err => console.error("Heartbeat failed:", err));
    }, 45000);

    return () => {
      clearInterval(heartbeatIntervalRef.current);
      unsubscribe();
      // Safely delete our participant node on unmount
      deleteDoc(participantDocRef).catch(e => console.error("Error removing participant:", e));
    };
  }, [roomId, userId, userName, isLeader]);

  // 3. Timer Countdown loop (Runs on both Leader & Participants, but only leader pushes state transitions)
  useEffect(() => {
    if (!session) return;
    
    const isRunning = session.state.status === 'running';
    
    if (isRunning) {
      timerIntervalRef.current = setInterval(() => {
        setLocalSecondsRemaining((prev) => {
          if (prev <= 1) {
            clearInterval(timerIntervalRef.current);
            if (isLeader) {
              handleTimerComplete();
            }
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    } else {
      clearInterval(timerIntervalRef.current);
    }

    return () => clearInterval(timerIntervalRef.current);
  }, [session, isLeader]);

  // 4. Leader Heartbeat (Every 15s when running, updates Firestore to keep participants accurately in sync)
  useEffect(() => {
    if (!isLeader || !session || session.state.status !== 'running') {
      clearInterval(leaderHeartbeatRef.current);
      return;
    }

    leaderHeartbeatRef.current = setInterval(() => {
      const sessionDocRef = doc(db, 'sessions', roomId);
      updateDoc(sessionDocRef, {
        'state.timerSecondsRemaining': localSecondsRemaining,
        'state.updatedAt': serverTimestamp()
      }).catch(err => console.error("Leader heartbeat sync failed:", err));
    }, 15000);

    return () => clearInterval(leaderHeartbeatRef.current);
  }, [isLeader, session, localSecondsRemaining, roomId]);

  // 5. Phase Transition Alert Sound Sync (Runs on all clients when Firestore updates phase)
  useEffect(() => {
    if (!session) return;
    const currentPhase = session.state.currentPhase;

    // Initialize ref on mount to prevent alarm playing on initial page load
    if (prevPhaseRef.current === null) {
      prevPhaseRef.current = currentPhase;
      return;
    }

    if (prevPhaseRef.current !== currentPhase) {
      console.log(`Study Session: Phase changed from ${prevPhaseRef.current} to ${currentPhase}. Playing alert chime.`);
      playAlertSound(currentPhase);
      prevPhaseRef.current = currentPhase;
    }
  }, [session]);

  // 6. Autoplay Bypass: Unlock Web Audio on first user interaction
  useEffect(() => {
    const handleGesture = () => {
      initAudioContext();
      window.removeEventListener('click', handleGesture);
      window.removeEventListener('touchstart', handleGesture);
      window.removeEventListener('keydown', handleGesture);
    };
    window.addEventListener('click', handleGesture);
    window.addEventListener('touchstart', handleGesture);
    window.addEventListener('keydown', handleGesture);
    return () => {
      window.removeEventListener('click', handleGesture);
      window.removeEventListener('touchstart', handleGesture);
      window.removeEventListener('keydown', handleGesture);
    };
  }, []);

  // Leader Actions
  const updateTimerStateInDb = async (newStatus, secondsRemaining, overrideFields = {}) => {
    if (!isLeader) return;
    const sessionDocRef = doc(db, 'sessions', roomId);
    try {
      await updateDoc(sessionDocRef, {
        'state.status': newStatus,
        'state.timerSecondsRemaining': secondsRemaining,
        'state.updatedAt': serverTimestamp(),
        ...overrideFields
      });
    } catch (e) {
      console.error("Failed to update timer state in DB:", e);
    }
  };

  const handleStartResume = () => {
    playAlertSound('click');
    updateTimerStateInDb('running', localSecondsRemaining, { 'state.lastAction': 'start' });
  };

  const handlePause = () => {
    playAlertSound('click');
    updateTimerStateInDb('paused', localSecondsRemaining, { 'state.lastAction': 'pause' });
  };

  const handleReset = () => {
    playAlertSound('click');
    let templateDuration = session.template.focusTime;
    if (session.state.currentPhase === 'shortBreak') {
      templateDuration = session.template.shortBreakTime;
    } else if (session.state.currentPhase === 'longBreak') {
      templateDuration = session.template.longBreakTime;
    }
    updateTimerStateInDb('idle', templateDuration, { 'state.lastAction': 'reset' });
    setLocalSecondsRemaining(templateDuration);
  };

  const handleSkip = () => {
    playAlertSound('click');
    transitionPhase(true);
  };

  const handleTimerComplete = () => {
    // Confetti explosion!
    confetti({
      particleCount: 150,
      spread: 80,
      origin: { y: 0.5 },
      colors: ['#ff8787', '#74c0fc', '#69db7c']
    });

    if (session.state.currentPhase === 'longBreak') {
      // Complete the cycle! Stop the timer, mark cycleCompleted in DB
      if (isLeader) {
        updateTimerStateInDb('paused', 0, { 
          'state.cycleCompleted': true,
          'state.lastAction': 'complete'
        });
      }
    } else {
      transitionPhase(false);
    }
  };

  const handleContinueSession = async () => {
    if (!isLeader) return;
    playAlertSound('click');
    const sessionDocRef = doc(db, 'sessions', roomId);
    try {
      await updateDoc(sessionDocRef, {
        'state.status': 'running',
        'state.currentPhase': 'focus',
        'state.currentInterval': 1,
        'state.timerSecondsRemaining': session.template.focusTime,
        'state.cycleCompleted': false,
        'state.lastAction': 'start',
        'state.updatedAt': serverTimestamp()
      });
    } catch (e) {
      console.error("Failed to continue session:", e);
    }
  };

  const handleDiscardSession = async () => {
    if (!isLeader) return;
    playAlertSound('click');
    
    // First, let's delete our participant document to trigger cleanup
    const participantDocRef = doc(db, 'sessions', roomId, 'participants', userId);
    try {
      await deleteDoc(participantDocRef);
    } catch (e) {
      console.error(e);
    }

    // Then delete the session document
    const sessionDocRef = doc(db, 'sessions', roomId);
    try {
      await deleteDoc(sessionDocRef);
      onLeave(); // redirect home
    } catch (e) {
      console.error("Failed to delete session:", e);
      onLeave(); // redirect home anyway
    }
  };

  const transitionPhase = (isSkipped = false) => {
    const currentPhase = session.state.currentPhase;
    let nextPhase = 'focus';
    let nextSeconds = session.template.focusTime;
    let nextInterval = session.state.currentInterval;

    if (currentPhase === 'focus') {
      // Determine if it is time for a long break or a short break
      if (session.state.currentInterval >= session.template.longBreakInterval) {
        nextPhase = 'longBreak';
        nextSeconds = session.template.longBreakTime;
        nextInterval = 1; // Reset intervals
      } else {
        nextPhase = 'shortBreak';
        nextSeconds = session.template.shortBreakTime;
        nextInterval = session.state.currentInterval + 1;
      }
    } else {
      // Break has ended, return to focus mode
      nextPhase = 'focus';
      nextSeconds = session.template.focusTime;
    }

    updateTimerStateInDb('running', nextSeconds, {
      'state.currentPhase': nextPhase,
      'state.currentInterval': nextInterval,
      'state.lastAction': isSkipped ? 'skip' : 'complete'
    });
  };

  const saveSettings = async (e) => {
    e.preventDefault();
    if (!isLeader) return;
    
    const focusSecs = focusInput * 60;
    const shortSecs = shortBreakInput * 60;
    const longSecs = longBreakInput * 60;
    
    let currentDuration = focusSecs;
    if (session.state.currentPhase === 'shortBreak') {
      currentDuration = shortSecs;
    } else if (session.state.currentPhase === 'longBreak') {
      currentDuration = longSecs;
    }

    const sessionDocRef = doc(db, 'sessions', roomId);
    try {
      await updateDoc(sessionDocRef, {
        'template.focusTime': focusSecs,
        'template.shortBreakTime': shortSecs,
        'template.longBreakTime': longSecs,
        'template.longBreakInterval': Number(intervalsInput),
        'state.status': 'idle',
        'state.timerSecondsRemaining': currentDuration,
        'state.updatedAt': serverTimestamp(),
        'state.lastAction': 'settings'
      });
      setShowSettings(false);
      playAlertSound('complete');
    } catch (e) {
      console.error("Failed to update settings:", e);
    }
  };

  const handleCopyLink = () => {
    const link = `${window.location.origin}${window.location.pathname}?room=${roomId}`;
    navigator.clipboard.writeText(link).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  if (!session) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen">
        <div className="neu-container p-8 text-center" style={{ maxWidth: '400px' }}>
          <p className="text-secondary">Loading study session...</p>
        </div>
      </div>
    );
  }

  // Format helper: seconds -> MM:SS
  const formatTime = (secs) => {
    const mins = Math.floor(secs / 60);
    const remainingSecs = secs % 60;
    return `${mins.toString().padStart(2, '0')}:${remainingSecs.toString().padStart(2, '0')}`;
  };

  // UI Helpers based on current phase
  const getPhaseConfig = () => {
    switch (session.state.currentPhase) {
      case 'shortBreak':
        return {
          title: 'Short Break',
          gradientClass: 'text-gradient-break',
          accentColor: 'var(--color-break)',
          pulseClass: 'pulse-active-break',
          maxTime: session.template.shortBreakTime
        };
      case 'longBreak':
        return {
          title: 'Long Break',
          gradientClass: 'text-gradient-long-break',
          accentColor: 'var(--color-long-break)',
          pulseClass: 'pulse-active-long-break',
          maxTime: session.template.longBreakTime
        };
      case 'focus':
      default:
        return {
          title: 'Study & Focus',
          gradientClass: 'text-gradient-focus',
          accentColor: 'var(--color-focus)',
          pulseClass: 'pulse-active-focus',
          maxTime: session.template.focusTime
        };
    }
  };

  const phase = getPhaseConfig();
  const progressPercent = ((phase.maxTime - localSecondsRemaining) / phase.maxTime) * 100;
  
  // Circle stroke offset math
  const strokeRadius = 130;
  const strokeCircumference = 2 * Math.PI * strokeRadius;
  const strokeDashoffset = strokeCircumference - (progressPercent / 100) * strokeCircumference;

  const getActionStatusMessage = () => {
    if (!session || !session.state) return '';
    const lastAction = session.state.lastAction;
    const leader = session.leaderName;
    
    switch (lastAction) {
      case 'start':
        return `▶️ ${leader} started the timer`;
      case 'pause':
        return `⏸️ ${leader} paused the timer`;
      case 'reset':
        return `🔄 ${leader} reset the timer`;
      case 'skip':
        return `⏭️ ${leader} skipped to the next phase`;
      case 'complete':
        return `🎉 Phase completed automatically`;
      case 'settings':
        return `⚙️ ${leader} updated session settings`;
      default:
        return `🕒 Room created. Waiting for leader.`;
    }
  };

  return (
    <div className="w-full max-w-5xl mx-auto my-auto px-4 py-2 md:py-8 flex flex-col items-center gap-4 md:gap-8">
      {/* 1. Header Card */}
      <header className="w-full flex flex-row justify-between items-center gap-2 p-3 md:py-4 md:px-8 neu-container" style={{ borderRadius: '20px' }}>
        <div className="flex items-center gap-2">
          <span className="font-semibold text-sm md:text-lg text-primary tracking-wide whitespace-nowrap">🧠 Shutup & Study</span>
          <span className="text-muted hidden sm:inline">|</span>
          <span className="text-xs font-medium px-2 py-1 neu-card-sm text-secondary whitespace-nowrap" style={{ borderRadius: '8px' }}>
            Room: {roomId.slice(0, 6)}
          </span>
        </div>
        
        <div className="flex items-center gap-2 md:gap-4">
          <button 
            className="neu-button p-2" 
            style={{ width: '38px', height: '38px', borderRadius: '50%' }}
            onClick={() => setSoundEnabled(!soundEnabled)}
            title={soundEnabled ? "Mute sounds" : "Unmute sounds"}
          >
            {soundEnabled ? <Volume2 size={16} /> : <VolumeX size={16} className="text-muted" />}
          </button>
          
          <button className="neu-button p-2 md:px-4 md:py-2 flex items-center gap-1.5" style={{ minHeight: '38px' }} onClick={handleCopyLink}>
            {copied ? <Check size={16} className="text-emerald-500" /> : <Copy size={16} />}
            <span className="hidden md:inline">Invite Link</span>
          </button>

          <button className="neu-button p-2 md:px-4 md:py-2 text-rose-500 hover:text-rose-600 flex items-center gap-1.5" style={{ minHeight: '38px' }} onClick={onLeave}>
            <LogOut size={16} />
            <span className="hidden md:inline">Leave</span>
          </button>
        </div>
      </header>

      {/* 2. Main Grid Layout */}
      <div className="w-full grid grid-cols-1 lg:grid-cols-3 gap-4 md:gap-8 items-start">
        
        {/* Left Card: Active Students */}
        <section className="lg:col-span-1 study-buddies-panel neu-container p-6 flex flex-col gap-6 min-h-[400px]">
          <h2 className="text-lg font-semibold text-secondary flex items-center gap-2 border-b border-gray-200 pb-3">
            <Users size={18} />
            Study Buddies ({participants.length})
          </h2>
          
          <div className="flex flex-col gap-3 overflow-y-auto max-h-[350px] pr-1">
            {participants.map((buddy) => {
              const isBuddyLeader = buddy.role === 'leader';
              const isBuddySelf = buddy.id === userId;
              
              return (
                <div key={buddy.id} className="neu-card-sm p-4 flex items-center justify-between border border-white/50">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-full flex items-center justify-center bg-gray-100" style={{ boxShadow: 'var(--shadow-pressed-sm)' }}>
                      {isBuddyLeader ? <Shield size={18} className="text-amber-500" /> : <User size={18} className="text-secondary" />}
                    </div>
                    <div>
                      <div className="font-semibold text-sm text-primary flex items-center gap-1.5">
                        {buddy.name}
                        {isBuddySelf && <span className="text-[10px] text-muted">(You)</span>}
                      </div>
                      <div className="text-xs text-muted">
                        {isBuddyLeader ? 'Leader' : 'Studying'}
                      </div>
                    </div>
                  </div>
                  
                  {/* Status Indicator */}
                  <span className="flex h-2.5 w-2.5 relative">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full opacity-75" style={{ backgroundColor: phase.accentColor }}></span>
                    <span className="relative inline-flex rounded-full h-2.5 w-2.5" style={{ backgroundColor: phase.accentColor }}></span>
                  </span>
                </div>
              );
            })}
          </div>
        </section>

        {/* Center/Right Card: Timer Wheel */}
        <main className="lg:col-span-2 w-full neu-container p-4 md:p-8 flex flex-col items-center justify-center min-h-[360px] md:min-h-[500px] relative">
          {isLeader && (
            <button 
              className="absolute top-6 right-6 neu-button-round"
              style={{ width: '44px', height: '44px' }}
              onClick={() => setShowSettings(!showSettings)}
              title="Session Template Settings"
            >
              <Settings size={18} />
            </button>
          )}

          {/* Settings Overlay View */}
          {showSettings && isLeader ? (
            <form onSubmit={saveSettings} className="w-full max-w-md flex flex-col gap-6">
              <h2 className="text-xl font-bold text-center text-secondary mb-2 flex items-center justify-center gap-2">
                <Edit3 size={20} /> Edit Pomodoro Template
              </h2>
              
              <div className="grid grid-cols-2 gap-4">
                <div className="flex flex-col gap-2">
                  <label className="text-xs font-semibold text-secondary">Study Time (min)</label>
                  <input 
                    type="number" 
                    min="1" 
                    max="180" 
                    value={focusInput}
                    onChange={(e) => setFocusInput(e.target.value)}
                    className="neu-input" 
                    required 
                  />
                </div>
                
                <div className="flex flex-col gap-2">
                  <label className="text-xs font-semibold text-secondary">Short Break (min)</label>
                  <input 
                    type="number" 
                    min="1" 
                    max="60" 
                    value={shortBreakInput}
                    onChange={(e) => setShortBreakInput(e.target.value)}
                    className="neu-input" 
                    required 
                  />
                </div>
                
                <div className="flex flex-col gap-2">
                  <label className="text-xs font-semibold text-secondary">Long Break (min)</label>
                  <input 
                    type="number" 
                    min="1" 
                    max="120" 
                    value={longBreakInput}
                    onChange={(e) => setLongBreakInput(e.target.value)}
                    className="neu-input" 
                    required 
                  />
                </div>

                <div className="flex flex-col gap-2">
                  <label className="text-xs font-semibold text-secondary">Interval Count</label>
                  <input 
                    type="number" 
                    min="1" 
                    max="10" 
                    value={intervalsInput}
                    onChange={(e) => setIntervalsInput(e.target.value)}
                    className="neu-input" 
                    required 
                  />
                </div>
              </div>
              
              <div className="flex gap-4 mt-2">
                <button type="submit" className="neu-button flex-1 py-3 bg-emerald-50 text-emerald-700 font-semibold">
                  Save & Reset
                </button>
                <button 
                  type="button" 
                  className="neu-button flex-1 py-3 text-secondary" 
                  onClick={() => setShowSettings(false)}
                >
                  Cancel
                </button>
              </div>
            </form>
          ) : (
            <>
              {/* Pomodoro Timer Display */}
              <div className="flex flex-col items-center gap-4">
                <div className={`text-xs font-bold tracking-widest uppercase px-4 py-1.5 neu-card-sm text-secondary ${session.state.status === 'running' ? phase.pulseClass : ''}`} style={{ borderRadius: '20px' }}>
                  {phase.title}
                </div>
                
                {/* Neumorphic Circle Ring */}
                <div className="timer-dial">
                  {/* SVG Circle Progress */}
                  <svg className="timer-svg" viewBox="0 0 290 290">
                    {/* Inner Track (Neumorphic Inset appearance) */}
                    <circle
                      cx="145"
                      cy="145"
                      r={strokeRadius}
                      fill="transparent"
                      stroke="var(--track-color)"
                      strokeWidth="8"
                      className="opacity-40"
                    />
                    
                    {/* Active Progress */}
                    <circle
                      cx="145"
                      cy="145"
                      r={strokeRadius}
                      fill="transparent"
                      stroke={phase.accentColor}
                      strokeWidth="8"
                      strokeDasharray={strokeCircumference}
                      strokeDashoffset={strokeDashoffset}
                      strokeLinecap="round"
                      style={{
                        transition: session.state.status === 'running' ? 'stroke-dashoffset 1s linear' : 'stroke-dashoffset 0.3s ease',
                      }}
                    />
                  </svg>

                  {/* Inner Timer text */}
                  <div className="z-10 flex flex-col items-center">
                    <span 
                      className="text-5xl md:text-6xl font-extrabold tracking-tighter" 
                      style={{ 
                        fontFamily: 'var(--font-timer)', 
                        color: 'var(--text-primary)',
                        textShadow: '1px 1px 1px #fff'
                      }}
                    >
                      {formatTime(localSecondsRemaining)}
                    </span>
                    <span className="text-xs text-muted font-medium mt-1">
                      Session {session.state.currentInterval} / {session.template.longBreakInterval}
                    </span>
                  </div>
                </div>

                {/* Real-time Action status log */}
                <div className="text-xs text-secondary mt-1 px-4 py-2 neu-card-sm border border-white/50 flex items-center justify-center gap-2" style={{ borderRadius: '12px', minHeight: '36px', width: '280px', boxShadow: 'inset 2px 2px 5px var(--shadow-dark-sm), inset -2px -2px 5px var(--shadow-light)' }}>
                  <span>{getActionStatusMessage()}</span>
                </div>

                {/* Control Panel */}
                <div className="flex items-center gap-6 mt-4">
                  {isLeader ? (
                    <>
                      <button 
                        className="neu-button-round" 
                        onClick={handleReset}
                        title="Reset current phase"
                      >
                        <RotateCcw size={18} />
                      </button>

                      {session.state.status === 'running' ? (
                        <button 
                          className="neu-button-round" 
                          style={{ 
                            width: '72px', 
                            height: '72px', 
                            boxShadow: 'inset 4px 4px 8px var(--shadow-dark), inset -4px -4px 8px var(--shadow-light)',
                            backgroundColor: 'var(--pause-btn-bg)'
                          }} 
                          onClick={handlePause}
                          title="Pause Study Session"
                        >
                          <Pause size={24} className="text-amber-600" />
                        </button>
                      ) : (
                        <button 
                          className="neu-button-round" 
                          style={{ 
                            width: '72px', 
                            height: '72px', 
                            backgroundColor: 'var(--play-btn-bg)' 
                          }} 
                          onClick={handleStartResume}
                          title="Start/Resume Study Session"
                        >
                          <Play size={24} className="text-emerald-600 translate-x-[1.5px]" />
                        </button>
                      )}

                      <button 
                        className="neu-button-round" 
                        onClick={handleSkip}
                        title="Skip to next phase"
                      >
                        <SkipForward size={18} />
                      </button>
                    </>
                  ) : (
                    // Participant Lock State Screen Info
                    <div className="neu-card-sm py-3 px-6 text-xs text-secondary flex items-center gap-2 border border-amber-100 bg-amber-50/20" style={{ borderRadius: '20px' }}>
                      <Shield size={14} className="text-amber-500" />
                      <span>Controlled by Leader: <strong className="text-primary">{session.leaderName}</strong></span>
                    </div>
                  )}
                </div>
              </div>
            </>
          )}
          {session.state.cycleCompleted && (
            <div className="cycle-complete-overlay">
              <div className="w-full max-w-sm p-8 neu-container text-center flex flex-col gap-6 border border-white/60" style={{ boxShadow: 'var(--shadow-flat)' }}>
                <div className="text-4xl">🏆</div>
                <h2 className="text-xl font-bold text-primary">Pomodoro Cycle Complete!</h2>
                <p className="text-sm text-secondary leading-relaxed">
                  {isLeader 
                    ? "Outstanding work! You successfully finished the entire study cycle. Would you like to start a new cycle or close this room?"
                    : `Fantastic effort! You completed the full cycle. Waiting for the room leader (${session.leaderName}) to choose the next step...`
                  }
                </p>
                
                {isLeader ? (
                  <div className="flex flex-col gap-3 mt-2">
                    <button 
                      onClick={handleContinueSession}
                      className="neu-button w-full py-3.5 bg-emerald-50 border border-white/80 text-emerald-700 font-semibold"
                    >
                      Start New Cycle
                    </button>
                    <button 
                      onClick={handleDiscardSession}
                      className="neu-button w-full py-3.5 text-rose-500 font-semibold"
                    >
                      Discard & Close Room
                    </button>
                  </div>
                ) : (
                  <div className="flex justify-center items-center gap-2 text-xs text-secondary mt-2">
                    <span className="flex h-2.5 w-2.5 relative">
                      <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-amber-400 opacity-75"></span>
                      <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-amber-500"></span>
                    </span>
                    <span>Awaiting leader decision...</span>
                  </div>
                )}
              </div>
            </div>
          )}
        </main>
      </div>

      {/* 3. Footer / Motivational Bar */}
      <footer className="hidden md:flex w-full items-center justify-center p-4 neu-container text-xs text-muted text-center" style={{ borderRadius: '14px', maxWidth: '400px' }}>
        <span>🎯 Keep quiet. Keep study. Achieve your goals.</span>
      </footer>
    </div>
  );
}
