let isMuted = false;

try {
  isMuted = localStorage.getItem('voltflow_muted') === 'true';
} catch (e) {
  // Ignore
}

export function setMuteState(muted: boolean) {
  isMuted = muted;
  try {
    localStorage.setItem('voltflow_muted', String(muted));
  } catch (e) {
    // Ignore
  }
}

export function getMuteState(): boolean {
  return isMuted;
}

export function playSuccessSound() {
  if (isMuted) return;
  try {
    const AudioContextClass = window.AudioContext || (window as any).webkitAudioContext;
    if (!AudioContextClass) return;
    
    const audioCtx = new AudioContextClass();
    const now = audioCtx.currentTime;
    
    const osc1 = audioCtx.createOscillator();
    const gain1 = audioCtx.createGain();
    osc1.connect(gain1);
    gain1.connect(audioCtx.destination);
    
    osc1.type = 'sine';
    osc1.frequency.setValueAtTime(523.25, now); // C5
    gain1.gain.setValueAtTime(0.08, now);
    gain1.gain.exponentialRampToValueAtTime(0.001, now + 0.15);
    
    osc1.start(now);
    osc1.stop(now + 0.15);
    
    const osc2 = audioCtx.createOscillator();
    const gain2 = audioCtx.createGain();
    osc2.connect(gain2);
    gain2.connect(audioCtx.destination);
    
    osc2.type = 'sine';
    osc2.frequency.setValueAtTime(783.99, now + 0.08); // G5
    gain2.gain.setValueAtTime(0.08, now + 0.08);
    gain2.gain.exponentialRampToValueAtTime(0.001, now + 0.25);
    
    osc2.start(now + 0.08);
    osc2.stop(now + 0.25);
  } catch (e) {
    // ignore autoplay or blocker blocks
  }
}

export function playFailureSound() {
  if (isMuted) return;
  try {
    const AudioContextClass = window.AudioContext || (window as any).webkitAudioContext;
    if (!AudioContextClass) return;
    
    const audioCtx = new AudioContextClass();
    const now = audioCtx.currentTime;
    
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    osc.connect(gain);
    gain.connect(audioCtx.destination);
    
    osc.type = 'triangle';
    osc.frequency.setValueAtTime(220.00, now); // A3
    osc.frequency.linearRampToValueAtTime(174.61, now + 0.18); // F3
    gain.gain.setValueAtTime(0.12, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.18);
    
    osc.start(now);
    osc.stop(now + 0.18);
  } catch (e) {
    // ignore
  }
}
