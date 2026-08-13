import React, { useState, useEffect, useRef } from 'react';
import {
  Play, Pause, RotateCcw, SkipForward, Settings, Volume2, VolumeX, Check, LogOut
} from 'lucide-react';
import confetti from 'canvas-confetti';

export default function LocalTimer({ isActive, onStart, onLeave }) {

  // Load preferences from localStorage or use defaults
  const [focusInput, setFocusInput] = useState(() => {
    return Number(localStorage.getItem('shutup_study_local_focus')) || 25;
  });
  const [shortBreakInput, setShortBreakInput] = useState(() => {
    return Number(localStorage.getItem('shutup_study_local_short')) || 5;
  });
  const [longBreakInput, setLongBreakInput] = useState(() => {
    return Number(localStorage.getItem('shutup_study_local_long')) || 15;
  });
  const [intervalsInput, setIntervalsInput] = useState(() => {
    return Number(localStorage.getItem('shutup_study_local_intervals')) || 4;
  });
  const [soundEnabled, setSoundEnabled] = useState(() => {
    const saved = localStorage.getItem('shutup_study_local_sound');
    return saved !== 'false';
  });

  // Active Timer state
  const [currentPhase, setCurrentPhase] = useState('focus'); // 'focus' | 'shortBreak' | 'longBreak'
  const [status, setStatus] = useState('idle'); // 'idle' | 'running' | 'paused'
  const [currentInterval, setCurrentInterval] = useState(1);
  const [secondsRemaining, setSecondsRemaining] = useState(focusInput * 60);
  const [isEditing, setIsEditing] = useState(false);

  const timerIntervalRef = useRef(null);
  const audioContextRef = useRef(null);

  // Sync timer when config parameters change and timer is idle
  useEffect(() => {
    if (status === 'idle') {
      if (currentPhase === 'focus') {
        setSecondsRemaining(focusInput * 60);
      } else if (currentPhase === 'shortBreak') {
        setSecondsRemaining(shortBreakInput * 60);
      } else if (currentPhase === 'longBreak') {
        setSecondsRemaining(longBreakInput * 60);
      }
    }
  }, [focusInput, shortBreakInput, longBreakInput, currentPhase, status]);

  // Audio Synthesizer using Web Audio API
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

  // Timer loop effect
  useEffect(() => {
    if (status === 'running') {
      timerIntervalRef.current = setInterval(() => {
        setSecondsRemaining((prev) => {
          if (prev <= 1) {
            clearInterval(timerIntervalRef.current);
            handleTimerComplete();
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    } else {
      clearInterval(timerIntervalRef.current);
    }

    return () => clearInterval(timerIntervalRef.current);
  }, [status, currentPhase, currentInterval]);

  // Audio configuration activation on gesture
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

  const handleTimerComplete = () => {
    // Confetti explosion
    confetti({
      particleCount: 120,
      spread: 70,
      origin: { y: 0.6 },
      colors: ['#ff8787', '#74c0fc', '#69db7c']
    });

    if (currentPhase === 'focus') {
      if (currentInterval >= intervalsInput) {
        // Time for long break
        setCurrentPhase('longBreak');
        setSecondsRemaining(longBreakInput * 60);
        playAlertSound('longBreak');
      } else {
        // Time for short break
        setCurrentPhase('shortBreak');
        setSecondsRemaining(shortBreakInput * 60);
        playAlertSound('shortBreak');
      }
      setStatus('paused'); // Pause so user starts break deliberately
    } else {
      // Break is complete, go back to focus
      if (currentPhase === 'longBreak') {
        setCurrentInterval(1);
      } else {
        setCurrentInterval(prev => prev + 1);
      }
      setCurrentPhase('focus');
      setSecondsRemaining(focusInput * 60);
      playAlertSound('focus');
      setStatus('paused');
    }
  };

  const handleStartResume = () => {
    playAlertSound('click');
    setStatus('running');
  };

  const handlePause = () => {
    playAlertSound('click');
    setStatus('paused');
  };

  const handleReset = () => {
    playAlertSound('click');
    setStatus('idle');
    setCurrentInterval(1);
    setCurrentPhase('focus');
    setSecondsRemaining(focusInput * 60);
  };

  const handleSkip = () => {
    playAlertSound('click');
    if (currentPhase === 'focus') {
      if (currentInterval >= intervalsInput) {
        setCurrentPhase('longBreak');
        setSecondsRemaining(longBreakInput * 60);
      } else {
        setCurrentPhase('shortBreak');
        setSecondsRemaining(shortBreakInput * 60);
      }
      setStatus('paused');
    } else {
      if (currentPhase === 'longBreak') {
        setCurrentInterval(1);
      } else {
        setCurrentInterval(prev => prev + 1);
      }
      setCurrentPhase('focus');
      setSecondsRemaining(focusInput * 60);
      setStatus('paused');
    }
  };

  const saveSettings = (e) => {
    e.preventDefault();
    localStorage.setItem('shutup_study_local_focus', focusInput);
    localStorage.setItem('shutup_study_local_short', shortBreakInput);
    localStorage.setItem('shutup_study_local_long', longBreakInput);
    localStorage.setItem('shutup_study_local_intervals', intervalsInput);

    setSecondsRemaining(focusInput * 60);
    setCurrentPhase('focus');
    setCurrentInterval(1);
    setStatus('idle');
    setIsEditing(false);
    playAlertSound('click');
  };

  const toggleSound = () => {
    const nextSound = !soundEnabled;
    setSoundEnabled(nextSound);
    localStorage.setItem('shutup_study_local_sound', String(nextSound));
    if (nextSound) {
      setTimeout(() => playAlertSound('click'), 50);
    }
  };

  // Helper variables for rendering
  const formatTime = (secs) => {
    const m = Math.floor(secs / 60).toString().padStart(2, '0');
    const s = (secs % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  };

  const getTotalDuration = () => {
    if (currentPhase === 'focus') return focusInput * 60;
    if (currentPhase === 'shortBreak') return shortBreakInput * 60;
    return longBreakInput * 60;
  };

  const totalDuration = getTotalDuration();
  const progress = totalDuration > 0 ? (totalDuration - secondsRemaining) / totalDuration : 0;
  const strokeRadius = 82;
  const strokeCircumference = 2 * Math.PI * strokeRadius;
  const strokeDashoffset = strokeCircumference - (progress * strokeCircumference);

  const getPhaseDetails = () => {
    switch (currentPhase) {
      case 'shortBreak':
        return {
          label: 'Short Break',
          gradientClass: 'text-gradient-break',
          accentColor: 'var(--color-break)',
          pulseClass: 'pulse-active-break'
        };
      case 'longBreak':
        return {
          label: 'Long Break',
          gradientClass: 'text-gradient-long-break',
          accentColor: 'var(--color-long-break)',
          pulseClass: 'pulse-active-long-break'
        };
      default:
        return {
          label: 'Focus Session',
          gradientClass: 'text-gradient-focus',
          accentColor: 'var(--color-focus)',
          pulseClass: 'pulse-active-focus'
        };
    }
  };

  const phase = getPhaseDetails();

  // 1. Initial Teaser Screen (Matching User Mockup)
  if (!isActive) {
    return (
      <div className="neu-container p-6 flex flex-col gap-4 border border-white/60 w-full">
        <div className="flex flex-col gap-2">
          <h2 className="text-base font-bold text-primary flex items-center gap-2" style={{ fontWeight: '700' }}>
            <span style={{ fontSize: '1.25rem', lineHeight: '1' }}>⏱️</span> Solo Study Session
          </h2>
          <p className="text-xs text-secondary leading-relaxed" style={{ fontSize: '0.825rem', color: '#7e7e82' }}>
            Study alone with a simple, distraction-free offline Pomodoro timer. No internet or database connection required.
          </p>
        </div>
        <button
          type="button"
          onClick={() => {
            playAlertSound('click');
            if (onStart) onStart();
          }}
          className="neu-button w-full py-3.5 mt-2 bg-gray-50 border border-white/80 font-bold flex items-center justify-center text-sm text-primary"
        >
          Start Solo Timer
        </button>
      </div>
    );
  }

  // 2. Active Screen with Timer Dial and Controls
  return (
    <div className="neu-container p-6 flex flex-col gap-4 border border-white/60 w-full relative">
      {/* Top Header Row */}
      <div className="flex items-center justify-between border-b border-gray-200 pb-3 dark:border-white/5">
        <h2 className="text-base font-bold text-secondary flex items-center gap-2">
          <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 block"></span>
          Solo Study Session
        </h2>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={toggleSound}
            className="neu-button-round w-8 h-8 md:w-8 md:h-8 flex items-center justify-center"
            title={soundEnabled ? "Disable Sound" : "Enable Sound"}
          >
            {soundEnabled ? <Volume2 size={14} className="text-secondary" /> : <VolumeX size={14} className="text-muted" />}
          </button>
          <button
            type="button"
            onClick={() => {
              playAlertSound('click');
              setIsEditing(!isEditing);
            }}
            className={`neu-button-round w-8 h-8 md:w-8 md:h-8 flex items-center justify-center ${isEditing ? 'active' : ''}`}
            title="Timer Settings"
          >
            <Settings size={14} className="text-secondary" />
          </button>
          <button
            type="button"
            onClick={() => {
              playAlertSound('click');
              setStatus('paused');
              if (onLeave) onLeave();
            }}
            className="neu-button-round w-8 h-8 md:w-8 md:h-8 flex items-center justify-center text-rose-500 hover:text-rose-600"
            title="Exit Solo Session"
          >
            <LogOut size={14} />
          </button>
        </div>
      </div>

      {isEditing ? (
        /* Settings panel edit mode */
        <form onSubmit={saveSettings} className="flex flex-col gap-4 py-2">
          <h3 className="text-xs font-bold text-secondary uppercase tracking-wider text-center">Timer Configurations</h3>

          <div className="grid grid-cols-2 gap-3">
            <div className="flex flex-col gap-1">
              <label className="text-[10px] font-bold text-muted uppercase tracking-wider">Focus (m)</label>
              <input
                type="number"
                min="1"
                max="180"
                value={focusInput}
                onChange={(e) => setFocusInput(Math.max(1, Number(e.target.value)))}
                className="neu-input p-2.5 text-xs"
                required
              />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-[10px] font-bold text-muted uppercase tracking-wider">Short Break (m)</label>
              <input
                type="number"
                min="1"
                max="60"
                value={shortBreakInput}
                onChange={(e) => setShortBreakInput(Math.max(1, Number(e.target.value)))}
                className="neu-input p-2.5 text-xs"
                required
              />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-[10px] font-bold text-muted uppercase tracking-wider">Long Break (m)</label>
              <input
                type="number"
                min="1"
                max="120"
                value={longBreakInput}
                onChange={(e) => setLongBreakInput(Math.max(1, Number(e.target.value)))}
                className="neu-input p-2.5 text-xs"
                required
              />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-[10px] font-bold text-muted uppercase tracking-wider">Intervals</label>
              <input
                type="number"
                min="1"
                max="10"
                value={intervalsInput}
                onChange={(e) => setIntervalsInput(Math.max(1, Number(e.target.value)))}
                className="neu-input p-2.5 text-xs"
                required
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3 mt-2">
            <button 
              type="button" 
              onClick={() => {
                playAlertSound('click');
                setFocusInput(Number(localStorage.getItem('shutup_study_local_focus')) || 25);
                setShortBreakInput(Number(localStorage.getItem('shutup_study_local_short')) || 5);
                setLongBreakInput(Number(localStorage.getItem('shutup_study_local_long')) || 15);
                setIntervalsInput(Number(localStorage.getItem('shutup_study_local_intervals')) || 4);
                setIsEditing(false);
              }}
              className="neu-button w-full py-3.5 text-sm text-secondary font-semibold"
            >
              Cancel
            </button>
            <button 
              type="submit" 
              className="neu-button w-full py-3.5 bg-emerald-50 border border-white/80 text-emerald-700 font-bold flex items-center justify-center gap-1.5 text-sm"
            >
              <Check size={16} />
              Save
            </button>
          </div>
        </form>
      ) : (
        /* Timer Active screen */
        <div className="flex flex-col items-center justify-center py-2">
          {/* Circular Progress Ring */}
          <div
            className="relative flex items-center justify-center rounded-full"
            style={{
              width: '190px',
              height: '190px',
              backgroundColor: 'var(--bg-color)',
              boxShadow: '8px 8px 16px var(--shadow-dark), -8px -8px 16px var(--shadow-light), inset 2px 2px 5px rgba(0,0,0,0.02)',
              border: '6px solid var(--bg-color)'
            }}
          >
            {/* SVG Progress Circle */}
            <svg
              style={{
                position: 'absolute',
                width: '178px',
                height: '178px',
                transform: 'rotate(-90deg)',
                top: '50%',
                left: '50%',
                marginTop: '-89px',
                marginLeft: '-89px'
              }}
              viewBox="0 0 190 190"
            >
              {/* Background Track */}
              <circle
                cx="95"
                cy="95"
                r={strokeRadius}
                fill="transparent"
                stroke="var(--track-color)"
                strokeWidth="6"
                className="opacity-40"
              />
              {/* Foreground Animated Progress Ring */}
              <circle
                cx="95"
                cy="95"
                r={strokeRadius}
                fill="transparent"
                stroke={phase.accentColor}
                strokeWidth="6"
                strokeDasharray={strokeCircumference}
                strokeDashoffset={strokeDashoffset}
                strokeLinecap="round"
                style={{
                  transition: status === 'running' ? 'stroke-dashoffset 1s linear' : 'stroke-dashoffset 0.3s ease'
                }}
              />
            </svg>

            {/* Inner Clock Text */}
            <div className="z-10 flex flex-col items-center justify-center">
              <span
                className="text-3xl md:text-4xl font-extrabold tracking-tight"
                style={{
                  fontFamily: 'var(--font-timer)',
                  color: 'var(--text-primary)',
                  textShadow: '1px 1px 1px #fff'
                }}
              >
                {formatTime(secondsRemaining)}
              </span>
              <span className="text-[10px] text-muted font-bold mt-0.5 uppercase tracking-wider">
                Interval {currentInterval} / {intervalsInput}
              </span>
            </div>
          </div>

          {/* Current Phase Label */}
          <div className="mt-4 flex flex-col items-center">
            <span className={`text-sm font-extrabold ${phase.gradientClass} tracking-wide`}>
              {phase.label}
            </span>
            <span className="text-[10px] text-muted mt-0.5 font-medium">
              {status === 'running' ? 'Keep grinding!' : status === 'paused' ? 'Timer paused' : 'Ready to start'}
            </span>
          </div>

          {/* Action buttons */}
          <div className="flex items-center gap-4 mt-5">
            <button
              type="button"
              onClick={handleReset}
              className="neu-button-round w-9 h-9 md:w-9 md:h-9 flex items-center justify-center"
              title="Reset Timer"
            >
              <RotateCcw size={14} className="text-secondary" />
            </button>

            {status === 'running' ? (
              <button
                type="button"
                onClick={handlePause}
                className="control-btn-center w-12 h-12 md:w-12 md:h-12 bg-amber-500/10 text-amber-600 border border-amber-500/20"
                style={{ boxShadow: 'var(--shadow-flat-sm)', borderRadius: '50%' }}
                title="Pause Timer"
              >
                <Pause size={20} />
              </button>
            ) : (
              <button
                type="button"
                onClick={handleStartResume}
                className="control-btn-center w-12 h-12 md:w-12 md:h-12 bg-emerald-500/10 text-emerald-600 border border-emerald-500/20"
                style={{ boxShadow: 'var(--shadow-flat-sm)', borderRadius: '50%' }}
                title="Start Timer"
              >
                <Play size={20} className="translate-x-[1.5px]" />
              </button>
            )}

            <button
              type="button"
              onClick={handleSkip}
              className="neu-button-round w-9 h-9 md:w-9 md:h-9 flex items-center justify-center"
              title="Skip Phase"
            >
              <SkipForward size={14} className="text-secondary" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
