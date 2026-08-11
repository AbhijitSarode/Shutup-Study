import React, { useState, useEffect } from 'react';
import { 
  Plus, LogIn, Sparkles, BookOpen, Clock, User, ArrowRight, ShieldCheck 
} from 'lucide-react';
import { doc, setDoc, getDoc } from 'firebase/firestore';
import { db, serverTimestamp } from './firebase';
import StudySession from './components/StudySession';

export default function App() {
  const [roomId, setRoomId] = useState(null);
  const [userId, setUserId] = useState(null);
  const [userName, setUserName] = useState('');
  const [isLeader, setIsLeader] = useState(false);
  
  // Navigation states
  const [step, setStep] = useState('home'); // 'home' | 'join-room-gate' | 'create-room-setup' | 'active-session'
  const [inputRoomId, setInputRoomId] = useState('');
  const [joinError, setJoinError] = useState('');
  
  // Template states for room creation
  const [focusTime, setFocusTime] = useState(25);
  const [shortBreak, setShortBreak] = useState(5);
  const [longBreak, setLongBreak] = useState(15);
  const [intervals, setIntervals] = useState(4);

  // 1. Initialize User ID and Room ID from URL on load
  useEffect(() => {
    // Generate or retrieve persistent user ID
    let cachedUid = localStorage.getItem('shutup_study_uid');
    if (!cachedUid) {
      cachedUid = 'usr_' + Math.random().toString(36).substring(2, 15);
      localStorage.setItem('shutup_study_uid', cachedUid);
    }
    setUserId(cachedUid);

    // Retrieve cached name
    const cachedName = localStorage.getItem('shutup_study_username') || '';
    setUserName(cachedName);

    // Check URL parameters for a room
    const params = new URLSearchParams(window.location.search);
    const roomParam = params.get('room');
    
    if (roomParam) {
      setRoomId(roomParam.trim().toUpperCase());
      
      // If we already have a cached username, skip directly to the session
      if (cachedName) {
        checkAndJoinRoom(roomParam.trim().toUpperCase(), cachedName, cachedUid);
      } else {
        setStep('join-room-gate');
      }
    }
  }, []);

  const checkAndJoinRoom = async (targetRoomId, nameToUse, uidToUse) => {
    setJoinError('');
    try {
      const docRef = doc(db, 'sessions', targetRoomId);
      const snapshot = await getDoc(docRef);
      
      if (snapshot.exists()) {
        const data = snapshot.data();
        const leaderCheck = data.leaderId === uidToUse;
        
        setIsLeader(leaderCheck);
        setRoomId(targetRoomId);
        setStep('active-session');
        
        // Update URL to match current room
        const newUrl = `${window.location.origin}${window.location.pathname}?room=${targetRoomId}`;
        window.history.pushState({ path: newUrl }, '', newUrl);
      } else {
        setJoinError('Study room not found. Check the link or ID.');
        setStep('home');
        // Clean URL parameter
        const cleanUrl = `${window.location.origin}${window.location.pathname}`;
        window.history.pushState({ path: cleanUrl }, '', cleanUrl);
      }
    } catch (e) {
      console.error(e);
      setJoinError('Error connecting to database. Please try again.');
    }
  };

  const handleCreateRoom = async (e) => {
    e.preventDefault();
    if (!userName.trim()) return;

    // Save name
    localStorage.setItem('shutup_study_username', userName.trim());

    // Generate unique Room ID
    const newRoomId = Math.random().toString(36).substring(2, 8).toUpperCase();
    
    const sessionData = {
      createdAt: serverTimestamp(),
      leaderId: userId,
      leaderName: userName.trim(),
      template: {
        focusTime: focusTime * 60,
        shortBreakTime: shortBreak * 60,
        longBreakTime: longBreak * 60,
        longBreakInterval: Number(intervals)
      },
      state: {
        status: 'idle',
        currentPhase: 'focus',
        timerSecondsRemaining: focusTime * 60,
        currentInterval: 1,
        updatedAt: serverTimestamp()
      }
    };

    try {
      await setDoc(doc(db, 'sessions', newRoomId), sessionData);
      setIsLeader(true);
      setRoomId(newRoomId);
      setStep('active-session');
      
      // Update URL
      const newUrl = `${window.location.origin}${window.location.pathname}?room=${newRoomId}`;
      window.history.pushState({ path: newUrl }, '', newUrl);
    } catch (e) {
      console.error("Failed to create room:", e);
      setJoinError("Failed to initialize session in Firestore. Please check settings.");
    }
  };

  const handleJoinGateSubmit = (e) => {
    e.preventDefault();
    if (!userName.trim() || !roomId) return;
    localStorage.setItem('shutup_study_username', userName.trim());
    checkAndJoinRoom(roomId, userName.trim(), userId);
  };

  const handleManualJoin = (e) => {
    e.preventDefault();
    if (!inputRoomId.trim()) return;
    
    // Extracted room code from full url just in case user paste the full invite link
    let code = inputRoomId.trim();
    if (code.includes('?room=')) {
      code = code.split('?room=')[1].split('&')[0];
    }
    
    const finalCode = code.toUpperCase();
    setRoomId(finalCode);
    
    if (userName) {
      checkAndJoinRoom(finalCode, userName, userId);
    } else {
      setStep('join-room-gate');
    }
  };

  const handleLeaveSession = () => {
    setRoomId(null);
    setIsLeader(false);
    setStep('home');
    
    // Reset URL query params
    const cleanUrl = `${window.location.origin}${window.location.pathname}`;
    window.history.pushState({ path: cleanUrl }, '', cleanUrl);
  };

  // Render Screens
  if (step === 'active-session' && roomId) {
    return (
      <StudySession 
        roomId={roomId} 
        userId={userId} 
        userName={userName} 
        isLeader={isLeader} 
        onLeave={handleLeaveSession}
      />
    );
  }

  return (
    <div className="flex-1 w-full max-w-md mx-auto flex flex-col justify-center px-6 py-12">
      {/* Brand logo & tagline */}
      <div className="text-center mb-8 flex flex-col items-center">
        <div className="w-16 h-16 rounded-3xl neu-container flex items-center justify-center mb-4 bg-gray-50 border border-white/60">
          <BookOpen className="text-gray-700" size={32} />
        </div>
        <h1 className="text-3xl font-extrabold text-primary tracking-tight" style={{ fontFamily: 'var(--font-timer)' }}>
          Shutup & Study
        </h1>
        <p className="text-xs font-semibold text-muted tracking-wide uppercase mt-1">
          Synchronized Study Rooms
        </p>
      </div>

      {joinError && (
        <div className="neu-card-sm p-4 mb-6 border border-rose-100 bg-rose-50/20 text-rose-500 text-sm text-center">
          {joinError}
        </div>
      )}

      {/* Screen 1: Home dashboard */}
      {step === 'home' && (
        <div className="flex flex-col gap-6 w-full">
          
          {/* Section A: Create Room Button Card */}
          <div className="neu-container p-6 flex flex-col gap-4 border border-white/60">
            <h2 className="text-base font-bold text-secondary flex items-center gap-2">
              <Plus size={18} /> Create Study Session
            </h2>
            <p className="text-xs text-muted leading-relaxed">
              Design a custom pomodoro template. Anyone with the link can join your synchronized group clock.
            </p>
            <button 
              className="neu-button w-full py-3.5 mt-2 bg-gray-50 border border-white/80 font-semibold"
              onClick={() => setStep('create-room-setup')}
            >
              Start Session Creator
              <ArrowRight size={16} />
            </button>
          </div>

          {/* Section B: Join Room Card */}
          <div className="neu-container p-6 flex flex-col gap-4 border border-white/60">
            <h2 className="text-base font-bold text-secondary flex items-center gap-2">
              <LogIn size={18} /> Join Study Session
            </h2>
            <form onSubmit={handleManualJoin} className="flex flex-col gap-3">
              <input 
                type="text" 
                placeholder="Enter Room Code or Invite Link" 
                value={inputRoomId}
                onChange={(e) => setInputRoomId(e.target.value)}
                className="neu-input text-sm"
                required
              />
              <button 
                type="submit" 
                className="neu-button w-full py-3.5 font-semibold mt-1"
              >
                Join Room
              </button>
            </form>
          </div>
        </div>
      )}

      {/* Screen 2: Join Gate (User enters their name before joining) */}
      {step === 'join-room-gate' && (
        <div className="neu-container p-8 flex flex-col gap-6 border border-white/60">
          <div className="text-center">
            <h2 className="text-lg font-bold text-secondary flex items-center justify-center gap-2">
              <User size={18} /> Identify Yourself
            </h2>
            <p className="text-xs text-muted mt-1">
              You are joining Room: <strong className="text-secondary">{roomId}</strong>. Please set a display name.
            </p>
          </div>

          <form onSubmit={handleJoinGateSubmit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <label className="text-[10px] font-bold text-muted uppercase tracking-wider">Your Name</label>
              <input 
                type="text" 
                placeholder="e.g. Abhijit" 
                value={userName}
                onChange={(e) => setUserName(e.target.value)}
                className="neu-input"
                maxLength={20}
                required
                autoFocus
              />
            </div>

            <button 
              type="submit" 
              className="neu-button w-full py-3.5 mt-2 bg-emerald-50 border border-white/80 text-emerald-700 font-semibold flex items-center justify-center gap-2"
            >
              <span>Join Study Room</span>
              <Sparkles size={16} />
            </button>

            <button 
              type="button" 
              className="neu-button w-full py-3 text-secondary"
              onClick={() => setStep('home')}
            >
              Back to Dashboard
            </button>
          </form>
        </div>
      )}

      {/* Screen 3: Creator Setup Card */}
      {step === 'create-room-setup' && (
        <form onSubmit={handleCreateRoom} className="neu-container p-8 flex flex-col gap-5 border border-white/60">
          <div className="text-center">
            <h2 className="text-lg font-bold text-secondary flex items-center justify-center gap-2">
              <ShieldCheck size={18} className="text-secondary" /> Study Setup
            </h2>
            <p className="text-xs text-muted mt-1">Configure your group's pomodoro templates.</p>
          </div>

          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <label className="text-[10px] font-bold text-muted uppercase tracking-wider">Your Name</label>
              <input 
                type="text" 
                placeholder="Leader Name" 
                value={userName}
                onChange={(e) => setUserName(e.target.value)}
                className="neu-input text-sm"
                maxLength={20}
                required
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] font-bold text-muted uppercase tracking-wider flex items-center gap-1">
                  <Clock size={10} /> Focus (min)
                </label>
                <input 
                  type="number" 
                  min="1" 
                  max="180" 
                  value={focusTime}
                  onChange={(e) => setFocusTime(e.target.value)}
                  className="neu-input text-sm"
                  required
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] font-bold text-muted uppercase tracking-wider flex items-center gap-1">
                  <Clock size={10} /> Break (min)
                </label>
                <input 
                  type="number" 
                  min="1" 
                  max="60" 
                  value={shortBreak}
                  onChange={(e) => setShortBreak(e.target.value)}
                  className="neu-input text-sm"
                  required
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] font-bold text-muted uppercase tracking-wider flex items-center gap-1">
                  <Clock size={10} /> Long Break (m)
                </label>
                <input 
                  type="number" 
                  min="1" 
                  max="120" 
                  value={longBreak}
                  onChange={(e) => setLongBreak(e.target.value)}
                  className="neu-input text-sm"
                  required
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] font-bold text-muted uppercase tracking-wider flex items-center gap-1">
                  Interval Count
                </label>
                <input 
                  type="number" 
                  min="1" 
                  max="10" 
                  value={intervals}
                  onChange={(e) => setIntervals(e.target.value)}
                  className="neu-input text-sm"
                  required
                />
              </div>
            </div>
          </div>

          <div className="flex flex-col gap-3 mt-2">
            <button 
              type="submit" 
              className="neu-button w-full py-3.5 bg-emerald-50 border border-white/80 text-emerald-700 font-semibold"
            >
              Start & Create Room
            </button>
            <button 
              type="button" 
              className="neu-button w-full py-3 text-secondary"
              onClick={() => setStep('home')}
            >
              Back to Dashboard
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
