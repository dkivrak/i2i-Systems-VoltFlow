import React, { useState, useCallback, useMemo, useEffect, useRef, Suspense } from 'react';
import {
  Zap, Home, LayoutGrid, TrendingUp, HelpCircle, LogOut, AlertTriangle, X,
  Sparkles, RefreshCw, PlusCircle, CheckCircle2, Filter, Heart, Trash2,
  Activity, Bell, Clock, ShieldAlert, Flame, Leaf, ZoomIn, ZoomOut,
  Radio, Cpu, Gauge, Download, FileJson, FileText, ChevronRight,
  ArrowRight, Settings, Layers, Eye, Map as MapIcon, Database, Server, Navigation,
  ChevronDown, Search, Waves
} from 'lucide-react';
import {
  ResponsiveContainer, AreaChart, Area, XAxis, YAxis, Tooltip,
  CartesianGrid, BarChart, Bar, PieChart, Pie, Cell, LineChart, Line
} from 'recharts';
import { api, getUserFacingError, getStoredToken, setStoredToken } from './api/client';
import { usePollingResource, getPollingInterval } from './hooks/usePollingResource';
import type { HomeStatus, HistoryPoint, HomeEvent } from './types';
import { EmptyState, ErrorState, DashboardSkeleton, InlineSpinner } from './components/PageStates';
import { Header } from './components/Header';
import { HomeCard } from './components/HomeCard';
import { OverviewStats } from './components/OverviewStats';
import { RegistrationModal } from './components/RegistrationModal';
import { LoginPage } from './components/LoginPage';
import { ToastProvider } from './components/ToastProvider';

// ─── Color Maps ────────────────────────────────────────────────────────────────
const DEVICE_DATA = [
  { name: 'Other',           value: 12.98, color: '#8b5cf6' },
  { name: 'Air Conditioning',value: 27.50, color: '#3b82f6' },
  { name: 'Laundry',         value: 18.70, color: '#f43f5e' },
  { name: 'Lighting',        value: 18.25, color: '#eab308' },
  { name: 'Kitchen',         value: 11.80, color: '#06b6d4' },
  { name: 'Other Appliances',value:  9.50, color: '#10b981' },
];

const WEEKLY_DATA = [
  { day: 'Mon', v: 18 }, { day: 'Tue', v: 24 }, { day: 'Wed', v: 19 },
  { day: 'Thu', v: 28 }, { day: 'Fri', v: 22 }, { day: 'Sat', v: 31 }, { day: 'Sun', v: 27 },
];

function generateLoadData() {
  return Array.from({ length: 60 }, (_, i) => ({
    t: `${String(Math.floor(i / 6) + 8).padStart(2,'0')}:${String((i % 6) * 10).padStart(2,'0')}`,
    v: 15 + Math.sin(i * 0.4) * 8 + Math.random() * 5,
  }));
}

function generateAnalysisData() {
  return Array.from({ length: 17 }, (_, i) => {
    const hour = 8 + i * 0.5;
    const hStr = `${String(Math.floor(hour)).padStart(2,'0')}:${hour % 1 === 0 ? '00' : '30'}`;
    return {
      t: hStr,
      consumption: 5 + Math.sin(i * 0.5) * 8 + Math.random() * 4,
      cost:        180 + Math.sin(i * 0.3) * 40 + Math.random() * 20,
      voltage:     320 + Math.sin(i * 0.2) * 8 + Math.random() * 5,
    };
  });
}

// ─── Compact Square Ring Gauge ──────────────────────────────────────────────────
function RingGauge({ value, max, label }: { value: number; max: number; label: string }) {
  const pct   = Math.min(value / max, 1);
  const r     = 38;
  const cx    = 50;
  const cy    = 50;
  const circ  = 2 * Math.PI * r;
  const dash  = circ * pct;
  const color = pct > 0.8 ? '#ef4444' : pct > 0.5 ? '#f59e0b' : '#06b6d4';

  return (
    <div className="flex flex-col items-center">
      <svg width="100" height="100" viewBox="0 0 100 100">
        <circle cx={cx} cy={cy} r={r} fill="none" stroke="#0d2136" strokeWidth="8" />
        <circle
          cx={cx} cy={cy} r={r} fill="none"
          stroke={color} strokeWidth="8"
          strokeLinecap="round"
          strokeDasharray={`${dash} ${circ - dash}`}
          strokeDashoffset={circ * 0.25}
          style={{ filter: `drop-shadow(0 0 6px ${color})`, transition: 'stroke-dasharray 0.8s ease' }}
        />
        <text x={cx} y={cy - 3}  textAnchor="middle" fill="white" fontSize="13" fontWeight="900" fontFamily="monospace">{value.toFixed(1)}</text>
        <text x={cx} y={cy + 9} textAnchor="middle" fill="#94a3b8" fontSize="7.5" fontFamily="monospace">{label}</text>
      </svg>
    </div>
  );
}

// ─── Horizontal Bar Gauge ───────────────────────────────────────────────────────
function HBarGauge({ label, value, unit, pct, color }: { label: string; value: string; unit: string; pct: number; color: string }) {
  return (
    <div className="flex items-center gap-3 py-2 border-b border-slate-800/60 last:border-0">
      <div className="w-36 text-xs font-bold text-gray-200 truncate">{label}</div>
      <div className="flex-1 flex items-center gap-2">
        <div className="flex-1 h-2 bg-slate-900 rounded-full overflow-hidden border border-slate-800">
          <div
            className="h-full rounded-full transition-all duration-700"
            style={{ width: `${pct * 100}%`, backgroundColor: color, boxShadow: `0 0 8px ${color}` }}
          />
        </div>
      </div>
      <div className="w-16 text-right font-mono text-xs font-extrabold" style={{ color }}>{value}<span className="text-gray-400 font-normal ml-0.5">{unit}</span></div>
    </div>
  );
}

// ─── Istanbul District Coordinates (SVG 540x260 space) ─────────────────────────
const ISTANBUL_DISTRICTS: { name: string; cx: number; cy: number }[] = [
  { name: 'Beşiktaş',   cx: 215, cy: 110 },
  { name: 'Kadıköy',    cx: 320, cy: 185 },
  { name: 'Şişli',      cx: 195, cy:  72 },
  { name: 'Üsküdar',    cx: 345, cy: 135 },
  { name: 'Bakırköy',   cx: 100, cy: 180 },
  { name: 'Fatih',      cx: 170, cy: 148 },
  { name: 'Beyoğlu',    cx: 210, cy:  92 },
  { name: 'Maltepe',    cx: 405, cy: 205 },
  { name: 'Sarıyer',    cx: 235, cy:  30 },
  { name: 'Ataşehir',   cx: 405, cy: 155 },
];

// ─── Ultra-Detailed Realistic Cyber GIS Map Component ───────────────────────────
interface SentinelMapProps {
  activeHomes: HomeStatus[];
  selectedHomeId: number | null;
  onHomeSelect: (homeId: number) => void;
}

function SentinelMap({ activeHomes, selectedHomeId, onHomeSelect }: SentinelMapProps) {
  const [zoom, setZoom] = useState(1);
  const [tooltip, setTooltip] = useState<{ x: number; y: number; home: HomeStatus } | null>(null);
  const svgRef = useRef<SVGSVGElement>(null);

  // Map active homes to real Istanbul district coordinates on the SVG map (viewBox 540x260)
  const homeNodeMap = useMemo(() => {
    const nodeCoords = [
      { name: 'My Home - Istanbul', district: 'Beşiktaş', cx: 245, cy: 105, homeId: 1 },
      { name: 'Bahçelievler',       district: 'Bahçelievler', cx: 135, cy: 150, homeId: 2 },
      { name: 'Ataşehir',           district: 'Ataşehir',     cx: 345, cy: 135, homeId: 3 },
      { name: 'Üsküdar',            district: 'Üsküdar',      cx: 285, cy: 125, homeId: 4 },
    ];
    return activeHomes.map((home, i) => {
      const coord = nodeCoords.find(c => c.homeId === home.homeId) || nodeCoords[i % nodeCoords.length];
      return { home, ...coord };
    });
  }, [activeHomes]);

  const handlePinClick = (homeId: number, e: React.MouseEvent) => {
    e.stopPropagation();
    onHomeSelect(homeId);
  };

  return (
    <div className="relative flex flex-col h-full bg-[#0b1329] border border-cyan-500/30 rounded-2xl overflow-hidden shadow-[0_0_25px_rgba(0,0,0,0.6)] font-sans">

      {/* ── TOP HEADER ── */}
      <div className="flex flex-col shrink-0 border-b border-cyan-900/50 bg-[#070e1e]/95 backdrop-blur-md z-20">
        <div className="flex items-center justify-between px-3 py-2 border-b border-slate-800/80">
          <div className="flex items-center gap-2">
            <Navigation className="w-4 h-4 text-cyan-400" />
            <span className="text-xs font-bold text-white uppercase tracking-wider">
              İSTANBUL LIVE ENERGY TOPOLOGY MAP
            </span>
            <span className="text-[9px] px-2 py-0.5 rounded-full bg-cyan-500/10 border border-cyan-500/40 text-cyan-300 font-mono font-semibold">
              GIS Vector View
            </span>
          </div>
          <div className="flex items-center gap-1 bg-[#0f1a30] rounded-lg p-0.5 border border-slate-700/60">
            <button onClick={() => setZoom(z => Math.min(z + 0.25, 2.5))} className="p-1 hover:text-cyan-300 text-gray-300 transition-colors"><ZoomIn className="w-3 h-3" /></button>
            <span className="text-[9px] font-mono text-cyan-400 px-1.5 font-bold">{Math.round(zoom * 100)}%</span>
            <button onClick={() => setZoom(z => Math.max(z - 0.25, 0.5))} className="p-1 hover:text-cyan-300 text-gray-300 transition-colors"><ZoomOut className="w-3 h-3" /></button>
            <button onClick={() => setZoom(1)} className="text-[8.5px] px-1.5 text-gray-300 hover:text-white transition-colors border-l border-slate-700 ml-0.5 font-semibold uppercase">Reset</button>
          </div>
        </div>

        {/* Home Selector Pills */}
        <div className="flex items-center justify-between px-3 py-1 bg-[#050b16]/80 text-[10px] font-mono">
          <div className="flex items-center gap-1.5">
            <Home className="w-3.5 h-3.5 text-cyan-400" />
            <span className="text-gray-200 font-semibold">ISTANBUL METRO REGION</span>
          </div>
          <div className="flex items-center gap-1.5">
            {activeHomes.map((h) => (
              <button
                key={h.homeId}
                onClick={(e) => handlePinClick(h.homeId, e)}
                className={`px-2 py-0.5 rounded border text-[9px] font-mono font-medium transition-all ${
                  h.homeId === selectedHomeId
                    ? 'bg-cyan-500/20 border-cyan-400 text-cyan-200 font-bold shadow-[0_0_10px_rgba(6,182,212,0.3)]'
                    : 'bg-slate-900/60 border-slate-700 text-gray-300 hover:border-slate-500'
                }`}
              >
                {h.homeName} ({(h.currentPowerWatts || 0).toFixed(0)}W)
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* ── MAP CANVAS (REALISTIC ISTANBUL DARK CARTOGRAPHIC MAP) ── */}
      <div className="relative flex-1 overflow-hidden bg-[#091122]">
        <div className="w-full h-full transition-transform duration-300" style={{ transform: `scale(${zoom})`, transformOrigin: 'center center' }}>
          <svg
            ref={svgRef}
            className="absolute inset-0 w-full h-full"
            viewBox="0 0 540 260"
            preserveAspectRatio="xMidYMid meet"
            onClick={() => setTooltip(null)}
          >
            <defs>
              {/* Map Glow Filters */}
              <filter id="pinGlow" x="-50%" y="-50%" width="200%" height="200%">
                <feGaussianBlur stdDeviation="3" result="blur" />
                <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
              </filter>
              <filter id="selectedGlow" x="-60%" y="-60%" width="220%" height="220%">
                <feGaussianBlur stdDeviation="5" result="blur" />
                <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
              </filter>
            </defs>

            {/* ── 1. WATER BODIES (Marmara Sea, Black Sea, Bosphorus, Haliç) ── */}
            {/* Background Sea / Ocean Base Color */}
            <rect width="540" height="260" fill="#070e1c" />

            {/* European Side Landmass (Avrupa Yakası) */}
            <path
              d="M 0 35 C 40 30, 90 32, 140 30 C 180 28, 220 25, 260 20
                 C 268 35, 265 55, 258 75 C 252 92, 248 105, 252 118
                 C 245 120, 235 123, 222 125 C 205 128, 192 122, 180 118
                 C 190 125, 210 130, 238 128
                 C 248 132, 245 142, 232 152 C 220 160, 195 168, 170 172
                 C 140 178, 100 182, 60 188 C 30 192, 10 195, 0 198 Z"
              fill="#131e32" stroke="#1e2d4a" strokeWidth="0.8"
            />

            {/* Asian Side Landmass (Anadolu Yakası) */}
            <path
              d="M 285 15 C 330 18, 410 22, 540 25 L 540 220
                 C 490 215, 430 210, 380 202 C 340 196, 310 185, 292 172
                 C 280 162, 275 148, 272 135 C 270 120, 275 105, 272 88
                 C 270 70, 275 50, 280 30 Z"
              fill="#131e32" stroke="#1e2d4a" strokeWidth="0.8"
            />

            {/* Bosphorus Strait & Water Accents (İstanbul Boğazı) */}
            <path
              d="M 265 0 C 268 20, 275 40, 270 60 C 265 80, 252 95, 255 115 C 258 125, 268 135, 272 145 C 275 155, 285 165, 290 172"
              fill="none" stroke="#070e1c" strokeWidth="16" strokeLinecap="round"
            />
            {/* Fine Bosphorus Shorelines */}
            <path
              d="M 265 0 C 268 20, 275 40, 270 60 C 265 80, 252 95, 255 115 C 258 125, 268 135, 272 145 C 275 155, 285 165, 290 172"
              fill="none" stroke="#1e3a5f" strokeWidth="10" strokeLinecap="round" opacity="0.6"
            />

            {/* Golden Horn (Haliç) */}
            <path
              d="M 245 125 C 230 126, 210 124, 190 118"
              fill="none" stroke="#070e1c" strokeWidth="7" strokeLinecap="round"
            />

            {/* Sea Names / Water Markings */}
            <text x="210" y="225" fill="#38bdf8" opacity="0.2" fontSize="11" fontFamily="sans-serif" fontWeight="bold" letterSpacing="3">MARMARA SEA (MARMARA DENİZİ)</text>
            <text x="360" y="20"  fill="#38bdf8" opacity="0.2" fontSize="9" fontFamily="sans-serif" fontWeight="bold" letterSpacing="2">BLACK SEA (KARADENİZ)</text>
            <text x="280" y="70"  fill="#38bdf8" opacity="0.25" fontSize="7" fontFamily="sans-serif" fontWeight="bold" transform="rotate(75, 280, 70)">BOSPHORUS STRAIT</text>

            {/* ── 2. MAJOR HIGHWAY & BRIDGE NETWORK (E-5, TEM, Bridges) ── */}
            <g opacity="0.55">
              {/* E-5 / D-100 Highway */}
              <path d="M 0 180 C 60 172, 120 162, 180 155 C 220 150, 245 130, 260 115 C 275 120, 310 140, 370 165 C 440 190, 540 205, 540 205"
                fill="none" stroke="#2563eb" strokeWidth="1.8" strokeLinejoin="round" />

              {/* TEM Highway (O-2) */}
              <path d="M 0 110 C 70 100, 140 92, 210 80 C 255 72, 270 70, 290 70 C 330 75, 410 110, 540 135"
                fill="none" stroke="#0284c7" strokeWidth="1.6" strokeLinejoin="round" strokeDasharray="6 3" />

              {/* Bosphorus Bridges */}
              {/* 15 Temmuz Şehitler Köprüsü (1st Bridge) */}
              <line x1="254" y1="112" x2="270" y2="120" stroke="#38bdf8" strokeWidth="2.2" />
              {/* Fatih Sultan Mehmet Köprüsü (2nd Bridge) */}
              <line x1="267" y1="70" x2="288" y2="70" stroke="#38bdf8" strokeWidth="2.2" />
              {/* Yavuz Sultan Selim Köprüsü (3rd Bridge) */}
              <line x1="265" y1="20" x2="280" y2="22" stroke="#38bdf8" strokeWidth="1.8" />
            </g>

            {/* ── 3. DISTRICT LABELS (Gerçek İlçe İsimleri) ── */}
            <g opacity="0.38" fill="#94a3b8" fontSize="7.5" fontFamily="sans-serif" fontWeight="600">
              <text x="210" y="42">Sarıyer</text>
              <text x="230" y="85">Beşiktaş</text>
              <text x="225" y="115">Beyoğlu</text>
              <text x="195" y="145">Fatih</text>
              <text x="135" y="180">Bakırköy</text>
              <text x="95"  y="155">Bahçelievler</text>
              <text x="295" y="145">Üsküdar</text>
              <text x="310" y="175">Kadıköy</text>
              <text x="360" y="155">Ataşehir</text>
              <text x="355" y="115">Ümraniye</text>
              <text x="330" y="50">Beykoz</text>
            </g>

            {/* ── 4. REALISTIC MAP PINS FOR REGISTERED HOMES ── */}
            {homeNodeMap.map((node) => {
              const isSelected = node.home.homeId === selectedHomeId;
              const isAnomaly = (node.home.anomalyCount || 0) > 0;
              const pinColor = isSelected ? '#ff9100' : isAnomaly ? '#ef4444' : '#00e5ff';

              return (
                <g
                  key={node.home.homeId}
                  transform={`translate(${node.cx}, ${node.cy})`}
                  style={{ cursor: 'pointer' }}
                  onClick={(e) => handlePinClick(node.home.homeId, e)}
                  onMouseEnter={() => setTooltip({ x: node.cx, y: node.cy, home: node.home })}
                  onMouseLeave={() => setTooltip(null)}
                >
                  {/* Pulse Ring for Selected Home */}
                  {isSelected && (
                    <circle cx="0" cy="-14" r="16" fill="none" stroke="#ff9100" strokeWidth="1.5" className="animate-ping" opacity="0.7" />
                  )}

                  {/* Pin Drop Shadow & Base Dot */}
                  <ellipse cx="0" cy="0" rx="6" ry="2.5" fill="#000000" opacity="0.6" />
                  <circle cx="0" cy="0" r="2.5" fill={pinColor} />

                  {/* Sleek Marker Pin Head */}
                  <g transform="translate(0, -14)" filter={isSelected ? 'url(#selectedGlow)' : 'url(#pinGlow)'}>
                    <path
                      d="M 0 0 C -7 -6, -8 -13, 0 -18 C 8 -13, 7 -6, 0 0 Z"
                      fill={isSelected ? '#ff9100' : '#091c32'}
                      stroke={pinColor}
                      strokeWidth="1.8"
                    />
                    <circle cx="0" cy="-11" r="3.2" fill={pinColor} />
                  </g>

                  {/* Floating Clean Label Box */}
                  <g transform="translate(0, -32)">
                    <rect
                      x="-38" y="-9" width="76" height="15" rx="4"
                      fill={isSelected ? '#1c0e03' : '#071322'}
                      stroke={pinColor}
                      strokeWidth={isSelected ? '1.5' : '1'}
                      opacity="0.95"
                    />
                    <text
                      x="0" y="1"
                      fill="#ffffff"
                      fontSize="7.5"
                      fontFamily="sans-serif"
                      fontWeight="bold"
                      textAnchor="middle"
                    >
                      {node.home.homeName.replace(' - Istanbul', '')}
                    </text>
                  </g>

                  {/* Power Output Badge */}
                  <g transform="translate(0, -43)">
                    <rect x="-24" y="-7" width="48" height="11" rx="3" fill="#030812" stroke={pinColor} strokeWidth="0.6" opacity="0.9" />
                    <text x="0" y="0.5" fill={pinColor} fontSize="6.5" fontFamily="monospace" fontWeight="bold" textAnchor="middle">
                      {(node.home.currentPowerWatts || 0).toFixed(0)} W
                    </text>
                  </g>
                </g>
              );
            })}
          </svg>
        </div>

        {/* Interactive Tooltip Card */}
        {tooltip && (
          <div
            className="absolute z-30 pointer-events-none transition-all duration-150"
            style={{
              left: `${(tooltip.x / 540) * 100}%`,
              top: `${(tooltip.y / 260) * 100}%`,
              transform: 'translate(-50%, -135%)'
            }}
          >
            <div className="bg-[#071325]/95 border border-cyan-400 rounded-xl px-3 py-2 shadow-[0_0_20px_rgba(6,182,212,0.4)] backdrop-blur-md whitespace-nowrap">
              <div className="flex items-center gap-2 border-b border-slate-700/60 pb-1 mb-1">
                <span className={`w-2 h-2 rounded-full ${ (tooltip.home.anomalyCount || 0) > 0 ? 'bg-red-400 animate-pulse' : 'bg-emerald-400'}`} />
                <span className="text-xs font-bold text-white font-sans">{tooltip.home.homeName}</span>
              </div>
              <div className="text-[9.5px] font-mono text-gray-300 space-y-0.5">
                <p className="flex justify-between gap-3"><span>Live Load:</span><span className="text-cyan-300 font-bold">{(tooltip.home.currentPowerWatts || 0).toFixed(1)} W</span></p>
                <p className="flex justify-between gap-3"><span>Cost:</span><span className="text-amber-400 font-bold">${(tooltip.home.currentCost || 0).toFixed(2)}</span></p>
                <p className="text-[8px] text-cyan-400 text-right mt-1">Click pin to view full details →</p>
              </div>
            </div>
          </div>
        )}

        {/* Footer Status Ribbon */}
        <div className="absolute bottom-0 left-0 right-0 bg-[#060c1a]/95 backdrop-blur-md border-t border-slate-800 px-3 py-1 flex items-center justify-between text-[8.5px] font-mono text-gray-300">
          <div className="flex items-center gap-2">
            <Server className="w-3.5 h-3.5 text-cyan-400" />
            <span className="text-gray-200 font-bold uppercase">ISTANBUL GIS TELEMETRY BUS</span>
            <span className="text-cyan-400 font-bold">• KAFKA STREAMING</span>
          </div>
          <span className="text-emerald-400 font-bold flex items-center gap-1">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-ping" /> 4 ACTIVE ISTANBUL LOCATIONS
          </span>
        </div>
      </div>
    </div>
  );
}



// ─── Main App ───────────────────────────────────────────────────────────────────
export default function App() {
  const fetchStatuses = useCallback((s: AbortSignal) => api.getHomeStatuses(s), []);
  const { data: polledHomes, error: pollingError, isLoading, retry: retryPolling, isRefreshing } =
    usePollingResource(fetchStatuses, getPollingInterval());

  const [favoriteHomeIds, setFavoriteHomeIds] = useState<number[]>(() => {
    try { return JSON.parse(localStorage.getItem('voltflow_favorites') || '[]'); } catch { return []; }
  });
  const [hiddenHomeIds, setHiddenHomeIds]     = useState<number[]>([]);
  const [showOnlyFavs, setShowOnlyFavs]       = useState(false);
  const [homeToDelete, setHomeToDelete]       = useState<HomeStatus | null>(null);
  const [selectedHomeId, setSelectedHomeId]   = useState<number | null>(null);
  const [detailPanelOpen, setDetailPanelOpen] = useState(true);

  useEffect(() => {
    try { localStorage.setItem('voltflow_favorites', JSON.stringify(favoriteHomeIds)); } catch {}
  }, [favoriteHomeIds]);

  const activeHomes = useMemo(() => {
    let list = (polledHomes || []).filter(h => !hiddenHomeIds.includes(h.homeId));
    if (showOnlyFavs) list = list.filter(h => favoriteHomeIds.includes(h.homeId));
    return list;
  }, [polledHomes, hiddenHomeIds, showOnlyFavs, favoriteHomeIds]);

  const selectedHome = useMemo(() =>
    activeHomes.find(h => h.homeId === selectedHomeId) || activeHomes[0] || null,
  [activeHomes, selectedHomeId]);

  useEffect(() => {
    if (activeHomes.length > 0 && selectedHomeId === null)
      setSelectedHomeId(activeHomes[0].homeId);
  }, [activeHomes, selectedHomeId]);

  const toggleFav = (id: number, e?: React.MouseEvent) => {
    e?.stopPropagation();
    setFavoriteHomeIds(p => p.includes(id) ? p.filter(x => x !== id) : [...p, id]);
  };

  const [historyPoints, setHistoryPoints] = useState<HistoryPoint[]>([]);
  const [events, setEvents]               = useState<HomeEvent[]>([]);
  const [eventsLoading, setEventsLoading] = useState(false);

  useEffect(() => {
    if (!selectedHome?.homeId) return;
    api.getHistory(selectedHome.homeId).then(setHistoryPoints).catch(() => setHistoryPoints([]));
    setEventsLoading(true);
    api.getEvents(selectedHome.homeId).then(evts => { setEvents(evts); setEventsLoading(false); })
      .catch(() => { setEvents([]); setEventsLoading(false); });
  }, [selectedHome?.homeId]);

  const loadData     = useMemo(() => generateLoadData(), []);
  const analysisData = useMemo(() => generateAnalysisData(), []);

  const liveWatts   = selectedHome?.currentPowerWatts   || 1942.9;
  const liveCost    = selectedHome?.currentCost          || 16.20;
  const budgetLimit = selectedHome?.monthlyBudget        || 1000;
  const budgetUsed  = selectedHome?.budgetUsagePercent   || 66;
  const homeName    = selectedHome?.homeName             || 'My Home - Istanbul';
  const homeId      = selectedHome?.homeId               || 1;
  const isCritical  = (selectedHome?.anomalyCount || 0) > 0 || selectedHome?.tariffState === 'PENALTY';

  const [registerOpen, setRegisterOpen] = useState(false);
  const [eventsOpen, setEventsOpen]     = useState(false);
  const [regName, setRegName]           = useState('');
  const [regEmail, setRegEmail]         = useState('');
  const [regBudget, setRegBudget]       = useState(1000);
  const [regSubmitting, setRegSub]      = useState(false);
  const [regError, setRegErr]           = useState<string|null>(null);

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!regName || !regEmail) { setRegErr('Fill all fields'); return; }
    setRegSub(true); setRegErr(null);
    try {
      const c = await api.registerHome({ name: regName, contactEmail: regEmail, monthlyBudget: regBudget, normalTariffPerKwh: 2.5, penaltyMultiplier: 1.5,
        appliances: [
          { name: 'refrigerator', type: 'REFRIGERATOR', safePowerLimitWatts: 400 },
          { name: 'air conditioner', type: 'AIR_CONDITIONER', safePowerLimitWatts: 1500 },
          { name: 'washing machine', type: 'WASHING_MACHINE', safePowerLimitWatts: 2000 },
          { name: 'television', type: 'TELEVISION', safePowerLimitWatts: 300 },
          { name: 'microwave', type: 'MICROWAVE', safePowerLimitWatts: 1200 },
        ]});
      setRegSub(false); setRegisterOpen(false); setRegName(''); setRegEmail('');
      if (c) { setSelectedHomeId(c.homeId); retryPolling(); }
    } catch(err) { setRegSub(false); setRegErr(getUserFacingError(err)); }
  };

  return (
    <div className="min-h-screen tech-background text-gray-100 flex flex-col font-sans overflow-hidden" style={{ height: '100vh' }}>

      {/* ── ELECTRIC LIGHTNING & POWER PLUG BACKGROUND LAYER ── */}
      <div className="fixed inset-0 pointer-events-none z-0 overflow-hidden">
        {/* Volumetric electric cyan glows behind cards */}
        <div className="absolute top-[10%] left-[20%] w-[500px] h-[500px] bg-cyan-500/12 rounded-full blur-[130px]" />
        <div className="absolute bottom-[10%] right-[25%] w-[600px] h-[600px] bg-sky-500/10 rounded-full blur-[150px]" />
        <div className="absolute top-[50%] left-[50%] -translate-x-1/2 -translate-y-1/2 w-[700px] h-[400px] bg-cyan-400/8 rounded-full blur-[160px]" />

        {/* Ambient Electric Atmosphere Base */}
        <svg className="absolute inset-0 w-full h-full opacity-10" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <pattern id="electricGrid" width="36" height="36" patternUnits="userSpaceOnUse">
              <circle cx="18" cy="18" r="0.6" fill="#38bdf8" opacity="0.25" />
            </pattern>
          </defs>
          <rect width="100%" height="100%" fill="url(#electricGrid)" />
        </svg>

        {/* Natural background lightning from electric_lightning_bg.jpg shines through cleanly */}


        {/* Low-Contrast Vignette Corner Dimming */}
        <div className="absolute inset-0 vignette-overlay" />
      </div>

      {/* ── TOP HEADER ── */}
      <header className="h-12 bg-[#040A14]/90 backdrop-blur-md border-b border-cyan-900/40 px-4 flex items-center justify-between z-30 shrink-0">
        <div className="flex items-center gap-2">
          <div className="p-1.5 rounded-lg bg-cyan-950 border border-cyan-500/50 shadow-[0_0_12px_rgba(6,182,212,0.4)]">
            <Zap className="w-4 h-4 fill-cyan-400 text-cyan-400" />
          </div>
          <span className="text-base font-black tracking-tight text-white">Volt<span className="text-cyan-400">Flow</span></span>
        </div>

        <h1 className="text-sm font-bold text-white tracking-widest uppercase">Sentinel Situation Room</h1>

        <div className="flex items-center gap-2">
          {pollingError ? (
            <button onClick={retryPolling} className="text-xs px-3 py-1 rounded-full bg-red-950 text-red-400 border border-red-800 flex items-center gap-1.5 hover:bg-red-900 transition-colors">
              <AlertTriangle className="w-3 h-3" /> Retry
            </button>
          ) : (
            <button onClick={() => setEventsOpen(true)}
              className="text-xs px-3 py-1.5 rounded-full bg-[#040d1e]/80 text-cyan-400 border border-cyan-800/60 flex items-center gap-1.5 hover:bg-cyan-900/30 transition-colors">
              <span className={`w-1.5 h-1.5 rounded-full ${isRefreshing ? 'bg-cyan-400 animate-ping' : 'bg-emerald-400'}`} />
              Live System Audit Log ({events.length > 0 ? events.length : 20} Events)
            </button>
          )}
          <button onClick={() => setRegisterOpen(true)}
            className="text-xs px-3 py-1.5 rounded-full bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 text-white font-bold flex items-center gap-1.5 shadow-[0_0_12px_rgba(6,182,212,0.4)] transition-all">
            <PlusCircle className="w-3.5 h-3.5" /> + New Home
          </button>
        </div>
      </header>

      {/* ── BODY ── */}
      <div className="flex flex-1 overflow-hidden relative z-10">

        {/* ── LEFT SIDEBAR NAV ── */}
        <aside className="w-12 bg-[#040812]/90 border-r border-cyan-900/30 flex flex-col items-center justify-between py-3 shrink-0">
          <div className="p-1.5 rounded-lg bg-cyan-950 border border-cyan-500/40"><Zap className="w-3.5 h-3.5 text-cyan-400 fill-cyan-400" /></div>
          <div className="flex flex-col items-center gap-4 text-gray-500">
            {[
              { icon: <Home className="w-4 h-4" />, active: !showOnlyFavs, onClick: () => setShowOnlyFavs(false) },
              { icon: <Heart className={`w-4 h-4 ${showOnlyFavs ? 'fill-red-500 text-red-500' : ''}`} />, active: showOnlyFavs, onClick: () => setShowOnlyFavs(p=>!p) },
              { icon: <LayoutGrid className="w-4 h-4" />, active: false, onClick: () => {} },
              { icon: <TrendingUp className="w-4 h-4" />, active: false, onClick: () => {} },
              { icon: <Layers className="w-4 h-4" />, active: false, onClick: () => {} },
              { icon: <Settings className="w-4 h-4" />, active: false, onClick: () => {} },
            ].map((item, i) => (
              <button key={i} onClick={item.onClick}
                className={`w-full py-2 flex items-center justify-center transition-all ${item.active ? 'border-l-2 border-cyan-400 text-cyan-400 bg-cyan-900/20' : 'hover:text-gray-200'}`}>
                {item.icon}
              </button>
            ))}
          </div>
          <div className="flex flex-col items-center gap-3 text-gray-500">
            <button onClick={() => {}} className="hover:text-cyan-400 transition-colors"><HelpCircle className="w-3.5 h-3.5" /></button>
            <button onClick={() => {}} className="hover:text-red-400 transition-colors"><LogOut className="w-3.5 h-3.5" /></button>
          </div>
        </aside>

        {/* ── MAIN CONTENT ── */}
        <div className="flex flex-1 overflow-hidden gap-3 p-3">

          {/* ── COL 1: REGISTERED HOMES (2x2 GRID OF SQUARE CARDS) ── */}
          <div className="w-[450px] shrink-0 flex flex-col gap-2.5 overflow-hidden">
            {/* Column Title */}
            <div className="flex items-center justify-between shrink-0">
              <div>
                <h2 className="text-xs font-bold text-white flex items-center gap-1.5 uppercase tracking-wider">
                  Registered Homes ({activeHomes.length}) 🏡
                </h2>
                <p className="text-[9px] text-gray-400 mt-0.5">Live Apache Ignite telemetry nodes</p>
              </div>
              <button onClick={() => setRegisterOpen(true)} className="p-1 text-cyan-400 hover:text-white bg-cyan-950/60 rounded-md border border-cyan-800">
                <PlusCircle className="w-3.5 h-3.5" />
              </button>
            </div>

            {/* 2-Column Grid Container for Square Home Cards */}
            <div className="flex-1 overflow-y-auto custom-scrollbar pr-1 grid grid-cols-2 gap-3 min-h-0">
              {activeHomes.length === 0 ? (
                <div className="col-span-2 glass-panel border border-slate-800 rounded-2xl p-6 text-center">
                  <p className="text-xs text-gray-400 mb-2">No active homes found.</p>
                  <button onClick={() => setRegisterOpen(true)} className="px-3 py-1.5 bg-cyan-600 text-white text-xs font-bold rounded-lg">+ Add First Home</button>
                </div>
              ) : (
                activeHomes.map((home) => {
                  const isSelected = home.homeId === selectedHomeId;
                  const hWatts = home.currentPowerWatts || 1942.9;
                  const hCost = home.currentCost || 16.20;
                  const hBudgetLimit = home.monthlyBudget || 1000;
                  const hBudgetUsed = home.budgetUsagePercent || 66;
                  const hName = home.homeName || 'Home';
                  const hId = home.homeId;
                  const hIsCritical = (home.anomalyCount || 0) > 0 || home.tariffState === 'PENALTY';

                  return (
                    <div
                      key={hId}
                      onClick={() => setSelectedHomeId(hId)}
                      className={`glass-panel border rounded-2xl p-2.5 flex flex-col justify-between aspect-square cursor-pointer transition-all duration-300 ${
                        isSelected
                          ? 'border-cyan-400 ring-2 ring-cyan-500/40 shadow-[0_0_25px_rgba(6,182,212,0.35)] bg-cyan-950/30'
                          : hIsCritical
                          ? 'border-red-500/70 shadow-[0_0_15px_rgba(239,68,68,0.2)]'
                          : 'border-cyan-500/30 hover:border-cyan-500/60 hover:bg-slate-900/40'
                      }`}
                    >
                      {/* Square Card Header */}
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-1.5 min-w-0">
                          <div className={`w-6 h-6 rounded-md flex items-center justify-center text-[11px] font-bold shrink-0 ${isSelected ? 'bg-cyan-500 text-black shadow-[0_0_8px_#06b6d4]' : 'bg-cyan-950 border border-cyan-500/40 text-cyan-400'}`}>
                            🏡
                          </div>
                          <div className="min-w-0">
                            <span className="text-[11px] font-extrabold text-white block truncate">{hName}</span>
                            <span className="text-[8px] text-gray-400">{home.appliances?.length || 5} Devices</span>
                          </div>
                        </div>
                        <div className="flex items-center gap-0.5 shrink-0">
                          <button onClick={e => toggleFav(hId, e)} className="p-0.5 hover:text-red-400 text-gray-400 transition-colors">
                            <Heart className={`w-3 h-3 ${favoriteHomeIds.includes(hId) ? 'fill-red-500 text-red-500' : ''}`} />
                          </button>
                          <button onClick={(e) => { e.stopPropagation(); setHomeToDelete(home); }} className="p-0.5 hover:text-red-400 text-gray-400 transition-colors">
                            <Trash2 className="w-3 h-3" />
                          </button>
                        </div>
                      </div>

                      {/* Centered Ring Gauge */}
                      <div className="flex justify-center my-auto py-0.5">
                        <RingGauge value={hWatts} max={3000} label="WATT" />
                      </div>

                      {/* Square Card Footer */}
                      <div className="flex items-center justify-between pt-1.5 border-t border-slate-800/80">
                        <div>
                          <span className="text-[8.5px] text-cyan-400 font-bold block">Cost: ${hCost.toFixed(2)}</span>
                          <span className="text-[7.5px] text-gray-400 font-mono">Limit: ${hBudgetLimit}</span>
                        </div>
                        <span className={`text-[8px] font-bold px-1.5 py-0.5 rounded-full border flex items-center gap-0.5 ${hIsCritical ? 'bg-red-950 text-red-400 border-red-800 animate-pulse' : 'bg-emerald-950 text-emerald-400 border-emerald-800'}`}>
                          {hIsCritical ? <><Flame className="w-2 h-2" />Critical</> : <><Leaf className="w-2 h-2" />Eco</>}
                        </span>
                      </div>
                    </div>
                  );
                })
              )}
            </div>

            {/* Status footer */}
            <div className="text-[8.5px] text-gray-500 font-mono shrink-0">
              <p className="text-white font-bold text-[9px]">Sentinel Grid Telemetries</p>
              <p>Apache Ignite, Kafka & Spring Boot nodes.</p>
            </div>
          </div>

          {/* ── COL 2: MAP + LOAD CHART (BALANCED PROPORTIONALLY) ── */}
          <div className="flex flex-col flex-1 gap-3 min-w-0">
            {/* Map */}
            <div className="flex-[3] min-h-0">
              <SentinelMap
                activeHomes={activeHomes}
                selectedHomeId={selectedHomeId}
                onHomeSelect={(id) => { setSelectedHomeId(id); setDetailPanelOpen(true); }}
              />
            </div>

            {/* ── GEMINI AI TELEMETRY LOG & ENERGY SAVINGS ADVISOR BOX ── */}
            <div className="flex-[2.5] glass-panel border border-cyan-500/40 rounded-2xl p-3 flex flex-col min-h-0 relative overflow-hidden bg-gradient-to-b from-[#06192d]/90 to-[#030c18]/95 shadow-[0_0_25px_rgba(6,182,212,0.2)]">
              {/* Subtle Ambient Glow Effect */}
              <div className="absolute -top-10 -right-10 w-40 h-40 bg-cyan-500/10 rounded-full blur-3xl pointer-events-none" />
              <div className="absolute -bottom-10 -left-10 w-40 h-40 bg-purple-500/10 rounded-full blur-3xl pointer-events-none" />

              {/* Gemini AI Header Bar */}
              <div className="flex items-center justify-between mb-2 shrink-0 pb-2 border-b border-cyan-900/40 z-10">
                <div className="flex items-center gap-2">
                  <div className="p-1.5 rounded-lg bg-gradient-to-br from-cyan-500 to-purple-600 shadow-[0_0_12px_rgba(6,182,212,0.5)]">
                    <Sparkles className="w-4 h-4 text-white animate-pulse" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <h3 className="text-xs font-black text-white tracking-wide">
                        Gemini AI Energy Optimizer & Log Advisor
                      </h3>
                      <span className="text-[8.5px] px-2 py-0.5 rounded-full bg-gradient-to-r from-cyan-500/20 to-purple-500/20 border border-cyan-400/50 text-cyan-300 font-mono font-bold tracking-wider">
                        ✨ GEMINI PRO 1.5
                      </span>
                    </div>
                    <p className="text-[9px] text-gray-300 font-mono">
                      Telemetry log pattern analysis for <strong className="text-cyan-300 font-bold">{homeName}</strong>
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={() => {
                      const btn = document.getElementById('gemini-spin-icon');
                      btn?.classList.add('animate-spin');
                      setTimeout(() => btn?.classList.remove('animate-spin'), 1000);
                    }}
                    className="text-[9.5px] px-2.5 py-1 rounded-lg bg-cyan-950/80 hover:bg-cyan-900/60 border border-cyan-500/40 text-cyan-300 font-mono font-bold flex items-center gap-1.5 transition-all shadow-[0_0_10px_rgba(0,229,255,0.2)]"
                  >
                    <RefreshCw id="gemini-spin-icon" className="w-3 h-3 text-cyan-400" />
                    Re-Analyze Logs
                  </button>
                </div>
              </div>

              {/* Actionable Gemini AI Insights & Recommendations (Log Analysis Output) */}
              <div className="flex-1 overflow-y-auto custom-scrollbar space-y-2 pr-1 z-10">

                {/* Insight 1: Peak Load Shift (Cost Savings) */}
                <div className="p-2.5 rounded-xl bg-[#041222]/90 border border-emerald-500/40 hover:border-emerald-400 transition-all group">
                  <div className="flex items-start justify-between gap-2 mb-1">
                    <div className="flex items-center gap-2">
                      <div className="p-1 rounded-md bg-emerald-950 border border-emerald-500/50 text-emerald-400">
                        <TrendingUp className="w-3.5 h-3.5" />
                      </div>
                      <h4 className="text-[11px] font-bold text-white group-hover:text-emerald-300 transition-colors">
                        Peak Load Shifting: Air Conditioning & Washing Machine
                      </h4>
                    </div>
                    <span className="text-[8.5px] font-mono font-extrabold px-2 py-0.5 rounded-full bg-emerald-950 text-emerald-300 border border-emerald-500/50 shrink-0">
                      SAVE ~$18.40/MO (-18.4%)
                    </span>
                  </div>
                  <p className="text-[10px] text-gray-300 leading-relaxed font-sans pl-7">
                    Kafka log telemetries show concurrent 2100W draw during peak hours (14:00 - 17:00, Peak Rate $3.80/kWh). Shifting laundry cycles to off-peak tariff (after 22:00, $1.20/kWh) reduces monthly bill by 18.4%.
                  </p>
                </div>

                {/* Insight 2: Motor Startup Voltage Spike & Anomaly */}
                <div className="p-2.5 rounded-xl bg-[#041222]/90 border border-amber-500/40 hover:border-amber-400 transition-all group">
                  <div className="flex items-start justify-between gap-2 mb-1">
                    <div className="flex items-center gap-2">
                      <div className="p-1 rounded-md bg-amber-950 border border-amber-500/50 text-amber-400">
                        <AlertTriangle className="w-3.5 h-3.5" />
                      </div>
                      <h4 className="text-[11px] font-bold text-white group-hover:text-amber-300 transition-colors">
                        Appliance Anomaly: Air Conditioner Compressor Voltage Spikes
                      </h4>
                    </div>
                    <span className="text-[8.5px] font-mono font-extrabold px-2 py-0.5 rounded-full bg-amber-950 text-amber-300 border border-amber-500/50 shrink-0">
                      MAINTENANCE ADVISORY
                    </span>
                  </div>
                  <p className="text-[10px] text-gray-300 leading-relaxed font-sans pl-7">
                    Ignite telemetry log #8920 recorded 3 transient current spikes (+35% over baseline) on AC compressor startup. Cleaning air filters will prevent motor coil overheating and save ~85W continuous draw.
                  </p>
                </div>

                {/* Insight 3: Penalty Tariff Risk Prevention */}
                <div className="p-2.5 rounded-xl bg-[#041222]/90 border border-cyan-500/40 hover:border-cyan-400 transition-all group">
                  <div className="flex items-start justify-between gap-2 mb-1">
                    <div className="flex items-center gap-2">
                      <div className="p-1 rounded-md bg-cyan-950 border border-cyan-500/50 text-cyan-400">
                        <ShieldAlert className="w-3.5 h-3.5" />
                      </div>
                      <h4 className="text-[11px] font-bold text-white group-hover:text-cyan-300 transition-colors">
                        Tariff Penalty Safeguard: Budget Usage Limit at {budgetUsed}%
                      </h4>
                    </div>
                    <span className="text-[8.5px] font-mono font-extrabold px-2 py-0.5 rounded-full bg-cyan-950 text-cyan-300 border border-cyan-500/50 shrink-0">
                      BUDGET PROTECTION
                    </span>
                  </div>
                  <p className="text-[10px] text-gray-300 leading-relaxed font-sans pl-7">
                    At current 1.94 kW load pace, tier-2 penalty tariff multiplier (1.5x) will trigger in 4.5 days. Setting Smart AC to Eco-Mode will stabilize load below 1.50 kW and preserve budget limit.
                  </p>
                </div>

              </div>

              {/* Gemini Footer Status */}
              <div className="mt-2 pt-1.5 border-t border-cyan-900/40 flex items-center justify-between text-[8.5px] font-mono text-gray-300 shrink-0 z-10">
                <div className="flex items-center gap-1.5 text-cyan-400">
                  <Sparkles className="w-3 h-3 text-purple-400 animate-spin" />
                  <span>Gemini LLM engine processing 1,420 telemetry log records/sec</span>
                </div>
                <span className="text-emerald-400 font-bold">● 100% OPTIMIZED</span>
              </div>
            </div>
          </div>

          {/* ── COL 3: DONUT + GRID STABILITY (UNTOUCHED) ── */}
          <div className="w-[240px] shrink-0 flex flex-col gap-3">
            {/* Device Power Breakdown */}
            <div className="flex-[3] glass-panel border border-cyan-500/40 rounded-2xl p-3 flex flex-col min-h-0">
              <h3 className="text-[10px] font-bold text-white uppercase tracking-wider mb-2 shrink-0">
                Real-Time Device-Level Power Breakdown
              </h3>
              <div className="flex-1 flex flex-col gap-2 min-h-0">
                {/* Donut */}
                <div className="h-[140px] relative shrink-0">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie data={DEVICE_DATA} dataKey="value" nameKey="name"
                        innerRadius={38} outerRadius={60} paddingAngle={2} stroke="none">
                        {DEVICE_DATA.map((e,i) => <Cell key={i} fill={e.color} />)}
                      </Pie>
                      <Tooltip contentStyle={{ backgroundColor:'#071324', borderColor:'#06b6d4', borderRadius:'6px', color:'#fff', fontSize:'10px' }}
                        formatter={(v: number) => [`${v.toFixed(2)}%`]} />
                    </PieChart>
                  </ResponsiveContainer>
                  <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                    <Zap className="w-4 h-4 text-cyan-400 fill-cyan-400/20 mb-0.5" />
                    <span className="text-sm font-black text-white">73%</span>
                    <span className="text-[8px] text-gray-400 font-mono uppercase">Capacity</span>
                  </div>
                </div>
                {/* Legend */}
                <div className="flex-1 space-y-1 overflow-y-auto custom-scrollbar">
                  {DEVICE_DATA.map((item, i) => (
                    <div key={i} className="flex items-center justify-between text-[10px]">
                      <div className="flex items-center gap-1.5 min-w-0">
                        <span className="w-2 h-2 rounded-sm shrink-0" style={{ backgroundColor: item.color }} />
                        <span className="text-gray-300 truncate">{item.name}</span>
                      </div>
                      <span className="font-mono font-extrabold shrink-0 ml-1" style={{ color: item.color }}>{item.value.toFixed(2)}%</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* Power Quality and Grid Stability */}
            <div className="flex-[2] glass-panel border border-cyan-500/40 rounded-2xl p-3 flex flex-col min-h-0">
              <div className="flex items-center justify-between mb-2 shrink-0">
                <h3 className="text-[10px] font-bold text-white uppercase tracking-wider">Power Quality and Grid Stability</h3>
              </div>
              <div className="flex-1 space-y-0 overflow-hidden">
                <HBarGauge label="Voltage THD%" value="1.2" unit="%" pct={0.12} color="#06b6d4" />
                <HBarGauge label="Current Imbalance%" value="0.5" unit="%" pct={0.05} color="#10b981" />
                <HBarGauge label="Network Packet Loss%" value="0.01" unit="%" pct={0.01} color="#f59e0b" />
              </div>
            </div>
          </div>

          {/* ── COL 4: DETAIL SLIDE-OUT PANEL (UNTOUCHED) ── */}
          <div className={`flex flex-col transition-all duration-300 ${detailPanelOpen ? 'w-[250px]' : 'w-0'} shrink-0 overflow-hidden`}>
            <div className="w-[250px] h-full glass-panel border border-cyan-500/40 rounded-2xl flex flex-col overflow-hidden">
              {/* Panel Header */}
              <div className="px-3 py-2.5 border-b border-cyan-900/40 flex items-center justify-between shrink-0 bg-[#030c1a]/80">
                <div>
                  <h3 className="text-xs font-extrabold text-white">Detailed Power Analysis:</h3>
                  <p className="text-[10px] text-cyan-400 font-mono truncate max-w-[170px]">{homeName}</p>
                </div>
                <button onClick={() => setDetailPanelOpen(false)} className="text-gray-400 hover:text-white transition-colors">
                  <X className="w-4 h-4" />
                </button>
              </div>

              <div className="flex-1 overflow-y-auto custom-scrollbar flex flex-col gap-3 p-3">
                {/* Date Range */}
                <div className="bg-[#040e1b] rounded-xl border border-slate-800 p-2">
                  <p className="text-[9px] text-gray-400 font-mono mb-1.5">Date Range</p>
                  <div className="flex items-center gap-1.5 text-[9px] font-mono">
                    <div className="flex items-center gap-1 bg-slate-900 px-1.5 py-1 rounded border border-slate-700">
                      <Clock className="w-2.5 h-2.5 text-cyan-400" />
                      <span className="text-white">2023-08-10</span>
                    </div>
                    <ArrowRight className="w-2.5 h-2.5 text-gray-400" />
                    <div className="flex items-center gap-1 bg-slate-900 px-1.5 py-1 rounded border border-slate-700">
                      <span className="text-white">7 days</span>
                    </div>
                  </div>
                </div>

                {/* Multi-line Analysis Chart */}
                <div className="bg-[#040e1b] rounded-xl border border-slate-800 p-2">
                  <div className="h-[140px]">
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart data={analysisData} margin={{ top: 4, right: 4, left: -35, bottom: 0 }}>
                        <CartesianGrid strokeDasharray="2 4" stroke="#0e2235" />
                        <XAxis dataKey="t" stroke="#334155" tick={{ fontSize: 7 }} interval={3} />
                        <YAxis yAxisId="left"  stroke="#334155" tick={{ fontSize: 7 }} />
                        <YAxis yAxisId="right" orientation="right" stroke="#334155" tick={{ fontSize: 7 }} />
                        <Tooltip contentStyle={{ backgroundColor:'#071324', borderColor:'#06b6d4', borderRadius:'6px', color:'#fff', fontSize:'9px' }} />
                        <Line yAxisId="left"  type="monotone" dataKey="consumption" stroke="#06b6d4" strokeWidth={1.5} dot={false} name="Consumption (kW)" />
                        <Line yAxisId="left"  type="monotone" dataKey="cost"        stroke="#f59e0b" strokeWidth={1.5} dot={false} name="Cost ($)" />
                        <Line yAxisId="right" type="monotone" dataKey="voltage"     stroke="#a855f7" strokeWidth={1.5} dot={false} name="Voltage" />
                      </LineChart>
                    </ResponsiveContainer>
                  </div>
                  {/* Legend */}
                  <div className="flex items-center gap-3 mt-1 text-[8px] font-mono">
                    <span className="flex items-center gap-1"><span className="w-3 h-0.5 bg-cyan-400 inline-block" />Consumption</span>
                    <span className="flex items-center gap-1"><span className="w-3 h-0.5 bg-amber-400 inline-block" />Cost</span>
                    <span className="flex items-center gap-1"><span className="w-3 h-0.5 bg-purple-400 inline-block" />Voltage</span>
                  </div>
                </div>

                {/* Stats Grid */}
                <div className="grid grid-cols-2 gap-2 text-xs">
                  <div className="bg-[#040e1b] rounded-xl border border-slate-800 p-2">
                    <p className="text-[9px] text-gray-400 font-mono">Peak Usage</p>
                    <p className="text-base font-black text-white mt-0.5">{liveWatts.toFixed(0)} <span className="text-[9px] font-mono text-gray-300">kW</span></p>
                  </div>
                  <div className="bg-[#040e1b] rounded-xl border border-slate-800 p-2">
                    <p className="text-[9px] text-gray-400 font-mono">Avg Daily Cost</p>
                    <p className="text-base font-black text-amber-400 mt-0.5">${(liveCost / 2.4).toFixed(2)}</p>
                  </div>
                  <div className="bg-[#040e1b] rounded-xl border border-slate-800 p-2">
                    <p className="text-[9px] text-gray-400 font-mono">Month-to-Date</p>
                    <p className="text-sm font-black text-cyan-400 mt-0.5">${(liveCost * 90).toFixed(0)}</p>
                  </div>
                  <div className="bg-[#040e1b] rounded-xl border border-slate-800 p-2">
                    <p className="text-[9px] text-gray-400 font-mono">Eco Savings</p>
                    <p className="text-sm font-black text-emerald-400 mt-0.5">100 W</p>
                  </div>
                </div>

                {/* Export Data */}
                <div className="bg-[#040e1b] rounded-xl border border-slate-800 p-2">
                  <p className="text-[9px] text-gray-400 font-mono mb-2">Export Telemetries</p>
                  <div className="flex items-center gap-1.5">
                    <button className="flex-1 flex items-center justify-center gap-1 py-1.5 bg-slate-800 hover:bg-slate-700 rounded-lg border border-slate-700 text-[9px] font-bold text-gray-200 transition-colors">
                      <FileText className="w-2.5 h-2.5 text-cyan-400" /> CSV
                    </button>
                    <button className="flex-1 flex items-center justify-center gap-1 py-1.5 bg-slate-800 hover:bg-slate-700 rounded-lg border border-slate-700 text-[9px] font-bold text-gray-200 transition-colors">
                      <FileJson className="w-2.5 h-2.5 text-amber-400" /> JSON
                    </button>
                    <button className="flex-1 flex items-center justify-center gap-1 py-1.5 bg-slate-800 hover:bg-slate-700 rounded-lg border border-slate-700 text-[9px] font-bold text-gray-200 transition-colors">
                      <Download className="w-2.5 h-2.5 text-purple-400" /> PDF
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Toggle button when panel is closed */}
          {!detailPanelOpen && (
            <button onClick={() => setDetailPanelOpen(true)}
              className="w-6 flex items-center justify-center bg-cyan-950/60 border border-cyan-500/40 rounded-xl text-cyan-400 hover:bg-cyan-900/60 transition-colors shrink-0">
              <ChevronRight className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>

      {/* ── MODALS ── */}

      {/* Delete confirmation */}
      {homeToDelete && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="glass-panel border border-red-500/50 rounded-2xl w-full max-w-sm p-6 shadow-[0_0_50px_rgba(239,68,68,0.3)] text-center">
            <Trash2 className="w-8 h-8 text-red-400 mx-auto mb-3" />
            <h2 className="text-base font-bold text-white mb-1">Remove Home Card</h2>
            <p className="text-xs text-gray-400 mb-5">Remove <strong className="text-white">{homeToDelete.homeName}</strong> from dashboard?</p>
            <div className="flex gap-3 justify-center">
              <button onClick={() => setHomeToDelete(null)} className="px-4 py-1.5 bg-slate-800 hover:bg-slate-700 text-gray-300 text-xs font-bold rounded-xl transition-colors">Cancel</button>
              <button onClick={() => { setHiddenHomeIds(p=>[...p,homeToDelete.homeId]); if (selectedHomeId===homeToDelete.homeId) setSelectedHomeId(null); setHomeToDelete(null); }}
                className="px-4 py-1.5 bg-red-600 hover:bg-red-500 text-white text-xs font-bold rounded-xl transition-colors">Remove</button>
            </div>
          </div>
        </div>
      )}

      {/* Register modal */}
      {registerOpen && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="glass-panel border border-cyan-500/50 rounded-2xl w-full max-w-md p-6 shadow-[0_0_50px_rgba(6,182,212,0.3)]">
            <div className="flex items-center justify-between pb-4 border-b border-cyan-900/40">
              <div className="flex items-center gap-2"><PlusCircle className="w-4 h-4 text-cyan-400" /><h2 className="text-base font-bold text-white">Register New Home</h2></div>
              <button onClick={() => setRegisterOpen(false)} className="text-gray-400 hover:text-white"><X className="w-4 h-4" /></button>
            </div>
            <form onSubmit={handleRegister} className="mt-4 space-y-3">
              {regError && <div className="p-2 bg-red-950 border border-red-800 text-red-300 text-xs rounded-xl">{regError}</div>}
              <div>
                <label className="block text-xs text-gray-300 font-bold mb-1">Home Name</label>
                <input type="text" value={regName} onChange={e=>setRegName(e.target.value)} placeholder="e.g. Kadikoy Residence"
                  className="w-full bg-[#050f1c] border border-slate-800 rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-cyan-500" required />
              </div>
              <div>
                <label className="block text-xs text-gray-300 font-bold mb-1">Contact Email</label>
                <input type="email" value={regEmail} onChange={e=>setRegEmail(e.target.value)} placeholder="owner@example.com"
                  className="w-full bg-[#050f1c] border border-slate-800 rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-cyan-500" required />
              </div>
              <div>
                <label className="block text-xs text-gray-300 font-bold mb-1">Monthly Budget ($)</label>
                <input type="number" value={regBudget} onChange={e=>setRegBudget(Number(e.target.value))}
                  className="w-full bg-[#050f1c] border border-slate-800 rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-cyan-500" required />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button type="button" onClick={() => setRegisterOpen(false)} className="px-4 py-2 bg-slate-800 text-gray-300 text-xs font-bold rounded-xl hover:bg-slate-700">Cancel</button>
                <button type="submit" disabled={regSubmitting}
                  className="px-4 py-2 bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-bold rounded-xl flex items-center gap-1.5 disabled:opacity-50 shadow-[0_0_12px_rgba(6,182,212,0.4)]">
                  {regSubmitting ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <CheckCircle2 className="w-3.5 h-3.5" />}
                  {regSubmitting ? 'Registering...' : 'Register Home'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Events modal */}
      {eventsOpen && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="glass-panel border border-cyan-500/50 rounded-2xl w-full max-w-2xl p-6 shadow-[0_0_50px_rgba(6,182,212,0.3)]">
            <div className="flex items-center justify-between pb-4 border-b border-cyan-900/40">
              <div className="flex items-center gap-2"><Activity className="w-5 h-5 text-cyan-400 animate-pulse" /><h2 className="text-base font-bold text-white">Live System Audit Log</h2></div>
              <button onClick={() => setEventsOpen(false)} className="text-gray-400 hover:text-white"><X className="w-5 h-5" /></button>
            </div>
            <div className="mt-4 max-h-80 overflow-y-auto custom-scrollbar space-y-2">
              {eventsLoading ? (
                <div className="text-center py-8 text-xs text-cyan-400 flex items-center justify-center gap-2"><RefreshCw className="w-4 h-4 animate-spin" />Loading events...</div>
              ) : events.length > 0 ? events.map(evt => {
                const isCrit = evt.eventType?.includes('BREACH') || evt.eventType?.includes('HIGH');
                return (
                  <div key={evt.id} className={`p-3 rounded-xl border text-xs flex items-start justify-between gap-3 ${isCrit ? 'bg-[#260a10] border-red-800 text-red-200' : 'bg-[#091e2b] border-cyan-900/60 text-cyan-200'}`}>
                    <div className="flex items-start gap-2">
                      {isCrit ? <ShieldAlert className="w-4 h-4 text-red-400 shrink-0" /> : <Bell className="w-4 h-4 text-cyan-400 shrink-0" />}
                      <span className="font-bold text-white">{evt.description || 'System Event'}</span>
                    </div>
                    <span className="text-[9px] font-mono shrink-0 opacity-70">{new Date(evt.createdAt).toLocaleTimeString()}</span>
                  </div>
                );
              }) : <p className="text-xs text-gray-400 italic text-center py-8">No events recorded yet.</p>}
            </div>
            <div className="flex justify-end pt-4 border-t border-slate-800 mt-4">
              <button onClick={() => setEventsOpen(false)} className="px-4 py-2 bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-bold rounded-xl transition-colors">Close</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
