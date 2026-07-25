import React from 'react';

export type ApplianceCharacterExpression =
  | 'idle'
  | 'observing'
  | 'active'
  | 'sleeping'
  | 'happy'
  | 'warning'
  | 'anomalous'
  | 'error'
  | 'disconnected'
  | 'loading'
  | 'success';

interface ApplianceCharacterProps {
  type:
    | 'REFRIGERATOR'
    | 'KETTLE'
    | 'OVEN'
    | 'TELEVISION'
    | 'WASHING_MACHINE'
    | 'AIR_CONDITIONER'
    | 'MICROWAVE'
    | 'LAMP'
    | 'COMPUTER';
  expression?: ApplianceCharacterExpression;
  lookOffset?: { x: number; y: number }; // normalized -4 to +4 pixels offset
  size?: number; // width/height in pixels
  className?: string;
}

export const ApplianceCharacter: React.FC<ApplianceCharacterProps> = ({
  type,
  expression = 'idle',
  lookOffset = { x: 0, y: 0 },
  size = 120,
  className = '',
}) => {
  // Determine colors based on type and states
  const colors = {
    REFRIGERATOR: { body: '#6366f1', door: '#818cf8', accent: '#312e81' },
    KETTLE: { body: '#06b6d4', base: '#0891b2', accent: '#0c4a6e' },
    OVEN: { body: '#f59e0b', glass: '#1e1b4b', border: '#d97706' },
    TELEVISION: { frame: '#374151', screen: '#1f2937', stand: '#4b5563' },
    WASHING_MACHINE: { body: '#10b981', drum: '#065f46', control: '#059669' },
    AIR_CONDITIONER: { body: '#e2e8f0', vent: '#cbd5e1', wind: '#60a5fa' },
    MICROWAVE: { body: '#ec4899', door: '#f472b6', panel: '#9d174d' },
    LAMP: { body: '#facc15', shade: '#eab308', glow: '#fef08a' },
    COMPUTER: { case: '#4b5563', screen: '#111827', stand: '#374151' },
  };

  const selectedColors = (colors[type] || colors.LAMP) as any;

  // Determine eyes and mouth paths based on expression
  const renderFace = () => {
    const pX = lookOffset.x;
    const pY = lookOffset.y;

    const isSleeping = expression === 'sleeping';
    const isHappy = ['happy', 'success'].includes(expression);
    const isAnomalous = ['anomalous', 'warning', 'error'].includes(expression);
    const isShy = expression === 'disconnected';

    // Face elements position offsets based on appliance type
    let faceCenter = { x: 50, y: 50 };
    if (type === 'REFRIGERATOR') faceCenter = { x: 50, y: 35 };
    if (type === 'KETTLE') faceCenter = { x: 45, y: 55 };
    if (type === 'OVEN') faceCenter = { x: 50, y: 30 };
    if (type === 'WASHING_MACHINE') faceCenter = { x: 50, y: 28 };
    if (type === 'AIR_CONDITIONER') faceCenter = { x: 50, y: 45 };
    if (type === 'MICROWAVE') faceCenter = { x: 40, y: 50 };
    if (type === 'LAMP') faceCenter = { x: 50, y: 35 };
    if (type === 'COMPUTER') faceCenter = { x: 50, y: 42 };

    const { x, y } = faceCenter;

    // Eye drawings
    let eyesSvg = null;
    if (isSleeping) {
      // Curved sleeping eyelids
      eyesSvg = (
        <g stroke="#1e293b" strokeWidth="2.5" fill="none" strokeLinecap="round">
          <path d={`M ${x - 12} ${y - 2} Q ${x - 7} ${y + 2} ${x - 2} ${y - 2}`} />
          <path d={`M ${x + 2} ${y - 2} Q ${x + 7} ${y + 2} ${x + 12} ${y - 2}`} />
        </g>
      );
    } else if (isHappy) {
      // Happy arches (^^)
      eyesSvg = (
        <g stroke="#111827" strokeWidth="3" fill="none" strokeLinecap="round">
          <path d={`M ${x - 12} ${y + 2} Q ${x - 7} ${y - 3} ${x - 2} ${y + 2}`} />
          <path d={`M ${x + 2} ${y + 2} Q ${x + 7} ${y - 3} ${x + 12} ${y + 2}`} />
        </g>
      );
    } else if (isAnomalous) {
      // Worried/dizzy crossed eyes or uneven sizes
      eyesSvg = (
        <g stroke="#991b1b" strokeWidth="3" fill="none" strokeLinecap="round">
          <path d={`M ${x - 11} ${y - 4} L ${x - 3} ${y + 4}`} />
          <path d={`M ${x - 3} ${y - 4} L ${x - 11} ${y + 4}`} />
          <path d={`M ${x + 3} ${y - 4} L ${x + 11} ${y + 4}`} />
          <path d={`M ${x + 11} ${y - 4} L ${x + 3} ${y + 4}`} />
        </g>
      );
    } else if (isShy) {
      // Shy / Cover eyes
      eyesSvg = (
        <g stroke="#475569" strokeWidth="3" fill="none" strokeLinecap="round">
          <path d={`M ${x - 10} ${y} L ${x - 4} ${y}`} />
          <path d={`M ${x + 4} ${y} L ${x + 10} ${y}`} />
        </g>
      );
    } else {
      // Standard expressive eyes with pointer-tracking pupils
      eyesSvg = (
        <g>
          {/* Left Eye */}
          <circle cx={x - 8} cy={y} r="6.5" fill="#ffffff" stroke="#111827" strokeWidth="1.8" />
          <circle cx={x - 8 + pX * 0.7} cy={y + pY * 0.7} r="3" fill="#111827" />
          {/* Right Eye */}
          <circle cx={x + 8} cy={y} r="6.5" fill="#ffffff" stroke="#111827" strokeWidth="1.8" />
          <circle cx={x + 8 + pX * 0.7} cy={y + pY * 0.7} r="3" fill="#111827" />
        </g>
      );
    }

    // Mouth drawings
    let mouthSvg = null;
    if (isSleeping) {
      // Small 'o' representing breathing/snoring
      mouthSvg = <circle cx={x} cy={y + 8} r="2.5" fill="none" stroke="#1e293b" strokeWidth="1.8" />;
    } else if (isHappy) {
      // Wide open smile
      mouthSvg = (
        <path
          d={`M ${x - 6} ${y + 7} Q ${x} ${y + 14} ${x + 6} ${y + 7} Z`}
          fill="#e11d48"
          stroke="#111827"
          strokeWidth="1.5"
        />
      );
    } else if (isAnomalous) {
      // Squiggly line mouth
      mouthSvg = (
        <path
          d={`M ${x - 7} ${y + 8} Q ${x - 3} ${y + 6} ${x} ${y + 8} T ${x + 7} ${y + 8}`}
          fill="none"
          stroke="#991b1b"
          strokeWidth="2.5"
          strokeLinecap="round"
        />
      );
    } else {
      // Simple cute smile or curious straight mouth
      mouthSvg = (
        <path
          d={`M ${x - 5} ${y + 7} Q ${x} ${y + 11} ${x + 5} ${y + 7}`}
          fill="none"
          stroke="#111827"
          strokeWidth="2"
          strokeLinecap="round"
        />
      );
    }

    return (
      <g>
        {eyesSvg}
        {mouthSvg}
      </g>
    );
  };

  // Render SVG based on appliance type
  const renderSVGContent = () => {
    const isAnomalous = ['anomalous', 'warning', 'error'].includes(expression);

    switch (type) {
      case 'REFRIGERATOR':
        return (
          <svg viewBox="0 0 100 100" width="100%" height="100%">
            {/* Fridge main body */}
            <rect x="25" y="10" width="50" height="78" rx="8" fill={selectedColors.body} stroke="#111827" strokeWidth="2.5" />
            {/* Split lines for upper/lower doors */}
            <line x1="25" y1="42" x2="75" y2="42" stroke="#111827" strokeWidth="2.5" />
            {/* Handles */}
            <rect x="29" y="32" width="4" height="8" rx="1.5" fill={selectedColors.accent} stroke="#111827" strokeWidth="1.2" />
            <rect x="29" y="46" width="4" height="12" rx="1.5" fill={selectedColors.accent} stroke="#111827" strokeWidth="1.2" />
            {/* Feet */}
            <rect x="33" y="88" width="8" height="4" fill="#1e293b" />
            <rect x="59" y="88" width="8" height="4" fill="#1e293b" />
            {/* Face */}
            {renderFace()}
            {/* Anomaly / Warning badge */}
            {isAnomalous && (
              <circle cx="75" cy="20" r="7" fill="#ef4444" stroke="#ffffff" strokeWidth="1.5" className="animate-bounce" />
            )}
          </svg>
        );

      case 'KETTLE':
        return (
          <svg viewBox="0 0 100 100" width="100%" height="100%">
            {/* Steam when active */}
            {expression === 'active' && (
              <path d="M 40 10 Q 42 5 40 2 T 40 -2 M 50 12 Q 52 7 50 4 T 50 0" fill="none" stroke="#22d3ee" strokeWidth="2" strokeLinecap="round" className="animate-bounce" />
            )}
            {/* Base platform */}
            <rect x="25" y="82" width="50" height="8" rx="3" fill="#1e293b" stroke="#111827" strokeWidth="2.5" />
            {/* Kettle pot */}
            <path d="M 30 40 C 30 25, 70 25, 70 40 L 73 80 L 27 80 Z" fill={selectedColors.body} stroke="#111827" strokeWidth="2.5" />
            {/* Spout */}
            <path d="M 27 45 L 18 35 L 20 33 L 30 42" fill={selectedColors.body} stroke="#111827" strokeWidth="2.5" />
            {/* Handle */}
            <path d="M 70 45 Q 85 50 82 72 L 72 75" fill="none" stroke="#111827" strokeWidth="6" strokeLinecap="round" />
            <path d="M 70 45 Q 85 50 82 72 L 72 75" fill="none" stroke={selectedColors.base} strokeWidth="2.5" strokeLinecap="round" />
            {/* Lid knob */}
            <circle cx="50" cy="23" r="4.5" fill={selectedColors.base} stroke="#111827" strokeWidth="1.5" />
            {/* Face */}
            {renderFace()}
          </svg>
        );

      case 'OVEN':
        return (
          <svg viewBox="0 0 100 100" width="100%" height="100%">
            {/* Outer box */}
            <rect x="18" y="15" width="64" height="66" rx="6" fill={selectedColors.body} stroke="#111827" strokeWidth="2.5" />
            {/* Top panel details (Knobs) */}
            <circle cx="28" cy="24" r="3" fill="#1e293b" />
            <circle cx="36" cy="24" r="3" fill="#1e293b" />
            <circle cx="64" cy="24" r="3" fill="#1e293b" />
            <circle cx="72" cy="24" r="3" fill="#1e293b" />
            {/* Oven glass window */}
            <rect x="25" y="42" width="50" height="32" rx="4" fill={expression === 'active' ? '#ffedd5' : selectedColors.glass} stroke="#111827" strokeWidth="2.5" />
            {/* Heating elements inside oven (active state) */}
            {expression === 'active' && (
              <g stroke="#f97316" strokeWidth="2" fill="none" className="animate-pulse">
                <path d="M 30 50 Q 50 48 70 50" />
                <path d="M 30 65 Q 50 63 70 65" />
              </g>
            )}
            {/* Feet */}
            <rect x="24" y="81" width="8" height="5" fill="#374151" />
            <rect x="68" y="81" width="8" height="5" fill="#374151" />
            {/* Face */}
            {renderFace()}
          </svg>
        );

      case 'TELEVISION':
        return (
          <svg viewBox="0 0 100 100" width="100%" height="100%">
            {/* Antenna */}
            <path d="M 40 10 L 50 22 L 60 10" fill="none" stroke="#4b5563" strokeWidth="2" />
            <circle cx="40" cy="10" r="2" fill="#4b5563" />
            <circle cx="60" cy="10" r="2" fill="#4b5563" />
            {/* TV Bezel */}
            <rect x="15" y="22" width="70" height="58" rx="6" fill={selectedColors.frame} stroke="#111827" strokeWidth="2.5" />
            {/* Inner screen */}
            <rect x="20" y="27" width="60" height="48" rx="3" fill={expression === 'active' ? '#38bdf8' : selectedColors.screen} stroke="#111827" strokeWidth="1.8" />
            {/* Scan lines when active */}
            {expression === 'active' && (
              <rect x="20" y="27" width="60" height="48" fill="url(#tvScanlines)" opacity="0.15" />
            )}
            {/* Face */}
            {renderFace()}
          </svg>
        );

      case 'WASHING_MACHINE':
        return (
          <svg viewBox="0 0 100 100" width="100%" height="100%">
            {/* Washer main body */}
            <rect x="18" y="15" width="64" height="70" rx="6" fill={selectedColors.body} stroke="#111827" strokeWidth="2.5" />
            {/* Soap drawer */}
            <rect x="24" y="21" width="16" height="8" rx="2" fill={selectedColors.control} stroke="#111827" strokeWidth="1.5" />
            {/* Dial */}
            <circle cx="70" cy="25" r="3.5" fill={selectedColors.control} stroke="#111827" strokeWidth="1.5" />
            {/* Outer door ring */}
            <circle cx="50" cy="58" r="21" fill={selectedColors.drum} stroke="#111827" strokeWidth="2.5" />
            {/* Water and clothes inside drum when active */}
            {expression === 'active' ? (
              <g className="animate-spin" style={{ transformOrigin: '50px 58px' }}>
                <circle cx="50" cy="58" r="15" fill="#7dd3fc" />
                <path d="M 40 50 C 45 42, 55 42, 60 50 C 55 58, 45 58, 40 50" fill="#f43f5e" />
                <path d="M 45 65 C 50 58, 60 58, 65 65" fill="#3b82f6" />
              </g>
            ) : (
              <circle cx="50" cy="58" r="15" fill="#047857" />
            )}
            {/* Glass shine overlay */}
            <circle cx="50" cy="58" r="15" fill="none" stroke="#ffffff" strokeWidth="2.5" strokeDasharray="30 45" opacity="0.3" />
            {/* Face */}
            {renderFace()}
          </svg>
        );

      case 'AIR_CONDITIONER':
        return (
          <svg viewBox="0 0 100 100" width="100%" height="100%">
            {/* AC Unit Body */}
            <rect x="12" y="32" width="76" height="30" rx="4" fill={selectedColors.body} stroke="#111827" strokeWidth="2.5" />
            {/* Vents grid bottom */}
            <line x1="16" y1="56" x2="84" y2="56" stroke="#94a3b8" strokeWidth="1.5" />
            {/* Status led */}
            <circle cx="80" cy="40" r="1.5" fill={expression === 'active' ? '#10b981' : '#f43f5e'} />
            {/* Wind lines when active */}
            {expression === 'active' && (
              <g stroke={selectedColors.wind} strokeWidth="2" strokeLinecap="round" opacity="0.8" className="animate-pulse">
                <path d="M 25 70 Q 22 78 20 86 M 50 72 Q 50 82 50 90 M 75 70 Q 78 78 80 86" fill="none" />
              </g>
            )}
            {/* Face */}
            {renderFace()}
          </svg>
        );

      case 'MICROWAVE':
        return (
          <svg viewBox="0 0 100 100" width="100%" height="100%">
            {/* Microwave Body */}
            <rect x="15" y="25" width="70" height="50" rx="5" fill={selectedColors.body} stroke="#111827" strokeWidth="2.5" />
            {/* Door Window */}
            <rect x="20" y="31" width="45" height="38" rx="3" fill={expression === 'active' ? '#fef08a' : '#1e1b4b'} stroke="#111827" strokeWidth="2" />
            {/* Dial and Buttons Panel */}
            <rect x="70" y="31" width="10" height="38" rx="2" fill={selectedColors.panel} stroke="#111827" strokeWidth="1.5" />
            <circle cx="75" cy="38" r="2.5" fill="#facc15" />
            <rect x="73" y="46" width="4" height="2" fill="#e2e8f0" />
            <rect x="73" y="51" width="4" height="2" fill="#e2e8f0" />
            <rect x="73" y="56" width="4" height="2" fill="#e2e8f0" />
            {/* Plate inside active microwave */}
            {expression === 'active' && (
              <ellipse cx="42" cy="62" rx="16" ry="3" fill="#cbd5e1" className="animate-pulse" />
            )}
            {/* Face */}
            {renderFace()}
          </svg>
        );

      case 'LAMP':
        return (
          <svg viewBox="0 0 100 100" width="100%" height="100%">
            {/* Glowing cone light when active */}
            {expression === 'active' && (
              <polygon points="50,42 20,95 80,95" fill="url(#lampGlowGrad)" opacity="0.45" />
            )}
            {/* Lamp base */}
            <path d="M 35 90 L 65 90 L 60 85 L 40 85 Z" fill="#4b5563" stroke="#111827" strokeWidth="2.5" />
            {/* Neck rod */}
            <path d="M 50 85 L 50 48" fill="none" stroke="#111827" strokeWidth="3" />
            {/* Shade (cone head) */}
            <path d="M 38 45 L 62 45 L 56 30 L 44 30 Z" fill={selectedColors.shade} stroke="#111827" strokeWidth="2.5" />
            {/* Little bolt switch */}
            <circle cx="50" cy="48" r="2" fill="#000" />
            {/* Face */}
            {renderFace()}
          </svg>
        );

      case 'COMPUTER':
        return (
          <svg viewBox="0 0 100 100" width="100%" height="100%">
            {/* Stand */}
            <path d="M 45 78 L 42 90 L 58 90 L 55 78 Z" fill={selectedColors.stand} stroke="#111827" strokeWidth="2" />
            {/* Monitor Outer Bezels */}
            <rect x="15" y="20" width="70" height="52" rx="4" fill={selectedColors.case} stroke="#111827" strokeWidth="2.5" />
            {/* Monitor screen */}
            <rect x="19" y="24" width="62" height="44" rx="2" fill={expression === 'active' ? '#a78bfa' : selectedColors.screen} stroke="#111827" strokeWidth="1.8" />
            {/* Keyboard preview */}
            <rect x="30" y="90" width="40" height="4" rx="1" fill="#4b5563" stroke="#111827" strokeWidth="1.5" />
            {/* Face */}
            {renderFace()}
          </svg>
        );

      default:
        return null;
    }
  };

  return (
    <div
      className={`appliance-character inline-block relative select-none ${className}`}
      style={{ width: `${size}px`, height: `${size}px` }}
      aria-label={`${type} character`}
    >
      <svg className="w-full h-full" xmlns="http://www.w3.org/2000/svg" style={{ position: 'absolute', width: 0, height: 0 }}>
        <defs>
          {/* TV Scanlines */}
          <pattern id="tvScanlines" width="4" height="4" patternUnits="userSpaceOnUse">
            <line x1="0" y1="0" x2="4" y2="0" stroke="#000" strokeWidth="1" opacity="0.3" />
          </pattern>
          {/* Lamp Light Glow Gradient */}
          <linearGradient id="lampGlowGrad" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stopColor="#fef08a" stopOpacity="0.8" />
            <stop offset="100%" stopColor="#fef08a" stopOpacity="0" />
          </linearGradient>
        </defs>
      </svg>
      <div className="absolute inset-0 w-full h-full">
        {renderSVGContent()}
      </div>
    </div>
  );
};
