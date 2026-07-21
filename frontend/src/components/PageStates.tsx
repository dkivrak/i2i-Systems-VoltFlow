import { HousePlus, RefreshCw, TriangleAlert } from 'lucide-react';

export function DashboardSkeleton() {
  return (
    <div className="dashboard-skeleton" aria-label="Evler yükleniyor" role="status">
      <span className="sr-only">Evler yükleniyor…</span>
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
      <span className="state-panel__icon" aria-hidden="true">
        <TriangleAlert size={24} />
      </span>
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
      <span className="state-panel__illustration" aria-hidden="true">
        <HousePlus size={31} />
      </span>
      <div>
        <p className="eyebrow">İlk adım</p>
        <h2>Enerji yolculuğunuz burada başlıyor</h2>
        <p>Bir ev ve cihazlarını ekleyin; VoltWise canlı tüketimi sizin için izlemeye başlasın.</p>
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
