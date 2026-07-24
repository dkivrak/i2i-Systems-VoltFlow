import {
  BarChart3,
  Clock3,
  HousePlus,
  PlugZap,
  RefreshCw,
  Sparkles,
  TriangleAlert,
  WifiOff,
} from 'lucide-react';
import { ApplianceCharacter } from '../characters';

export function DashboardSkeleton() {
  return (
    <div className="dashboard-skeleton" aria-label="Evler yükleniyor" role="status">
      <span className="sr-only">Evler yükleniyor…</span>
      <div className="dashboard-skeleton__character" aria-hidden="true">
        <ApplianceCharacter type="COMPUTER" state="loading" size="md" />
        <span className="dashboard-skeleton__signal" />
      </div>
      <div className="skeleton-stats">
        {Array.from({ length: 4 }, (_, index) => (
          <div className="skeleton skeleton--stat" key={index} />
        ))}
      </div>
      <div className="skeleton-grid">
        {Array.from({ length: 3 }, (_, index) => (
          <div className="skeleton skeleton--card" key={index} />
        ))}
      </div>
    </div>
  );
}

interface ErrorStateProps {
  message: string;
  onRetry: () => void;
  compact?: boolean;
}

export function ErrorState({ message, onRetry, compact = false }: ErrorStateProps) {
  return (
    <div className={`state-panel state-panel--error${compact ? ' state-panel--compact' : ''}`} role="alert">
      <div className="state-panel__character" aria-hidden="true">
        <ApplianceCharacter type="COMPUTER" state="error" size={compact ? 'sm' : 'md'} />
        <span className="state-panel__icon">
          <TriangleAlert size={20} />
        </span>
      </div>
      <div>
        <h3>Veriler alınamadı</h3>
        <p>{message}</p>
      </div>
      <button className="button button--secondary" type="button" onClick={onRetry}>
        <RefreshCw aria-hidden="true" size={16} /> Yeniden dene
      </button>
    </div>
  );
}

interface EmptyStateProps {
  onRegister: () => void;
}

export function EmptyState({ onRegister }: EmptyStateProps) {
  return (
    <div className="state-panel state-panel--empty">
      <div className="state-panel__character-group" aria-hidden="true">
        <span className="state-panel__house"><HousePlus size={31} /></span>
        <ApplianceCharacter type="REFRIGERATOR" state="observing" size="md" />
        <ApplianceCharacter type="LAMP" state="sleeping" size="sm" />
      </div>
      <div>
        <p className="eyebrow">İlk adım</p>
        <h2>Enerji yolculuğunuz burada başlıyor</h2>
        <p>Bir ev ve cihazlarını ekleyin; VoltFlow canlı tüketimi sizin için izlemeye başlasın.</p>
      </div>
      <button className="button button--primary" type="button" onClick={onRegister}>
        İlk evimi ekle
      </button>
    </div>
  );
}

export function InlineSpinner({ label = 'Yükleniyor' }: { label?: string }) {
  return (
    <span className="spinner-label" role="status">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </span>
  );
}

export function ChartLoadingState({ label = 'Grafik hazırlanıyor' }: { label?: string }) {
  return (
    <div className="section-state section-state--loading">
      <BarChart3 aria-hidden="true" size={21} />
      <InlineSpinner label={label} />
    </div>
  );
}

export function NoAppliancesState() {
  return (
    <div className="section-state section-state--empty">
      <div className="section-state__character" aria-hidden="true">
        <ApplianceCharacter type="KETTLE" state="sleeping" size="sm" />
      </div>
      <div>
        <h3>Henüz cihaz eklenmedi</h3>
        <p>Bu eve eklenen cihazlar canlı ölçümleriyle burada görünecek.</p>
      </div>
      <PlugZap aria-hidden="true" size={18} />
    </div>
  );
}

interface NoTelemetryStateProps {
  onRetry?: () => void;
}

export function NoTelemetryState({ onRetry }: NoTelemetryStateProps) {
  return (
    <div className="section-state section-state--stale" role="status">
      <div className="section-state__character" aria-hidden="true">
        <ApplianceCharacter type="TELEVISION" state="disconnected" size="sm" />
      </div>
      <div>
        <h3>Telemetri sinyali bekleniyor</h3>
        <p>Son ölçüm henüz ulaşmadı. Gösterilen değerlerin güncelliğini kontrol edin.</p>
      </div>
      {onRetry && (
        <button className="button button--secondary button--small" type="button" onClick={onRetry}>
          <RefreshCw aria-hidden="true" size={15} /> Yeniden bağlan
        </button>
      )}
    </div>
  );
}

interface StaleDataNoticeProps {
  lastUpdatedLabel?: string;
  onRetry: () => void;
}

export function StaleDataNotice({ lastUpdatedLabel, onRetry }: StaleDataNoticeProps) {
  return (
    <div className="stale-data-notice" role="status" aria-live="polite">
      <WifiOff aria-hidden="true" size={17} />
      <div>
        <strong>Canlı bağlantı gecikiyor</strong>
        <span>
          Son başarılı veriler gösteriliyor
          {lastUpdatedLabel ? ` · ${lastUpdatedLabel}` : ''}.
        </span>
      </div>
      <button type="button" onClick={onRetry}>
        <RefreshCw aria-hidden="true" size={14} /> Bağlanmayı dene
      </button>
    </div>
  );
}

export function RecommendationUnavailableState() {
  return (
    <div className="section-state section-state--quiet" role="status">
      <span className="section-state__icon" aria-hidden="true">
        <Sparkles size={19} />
      </span>
      <div>
        <h3>Öneri şu anda hazır değil</h3>
        <p>Canlı enerji verileri izlenmeye devam ediyor; yeni öneriler oluştuğunda burada görünecek.</p>
      </div>
      <Clock3 aria-hidden="true" size={17} />
    </div>
  );
}
