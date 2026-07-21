import { Plus, Zap } from 'lucide-react';

interface HeaderProps {
  isRefreshing: boolean;
  homeCount: number;
  onRegister: () => void;
}

export function Header({ isRefreshing, homeCount, onRegister }: HeaderProps) {
  return (
    <header className="app-header">
      <a className="brand" href="#main-content" aria-label="VoltWise ana içeriğe git">
        <span className="brand__mark" aria-hidden="true">
          <Zap size={21} strokeWidth={2.5} />
        </span>
        <span className="brand__wordmark">
          Volt<span>Wise</span>
        </span>
      </a>

      <div className="app-header__actions">
        <div className="live-indicator" aria-live="polite">
          <span className={`live-indicator__dot${isRefreshing ? ' is-refreshing' : ''}`} aria-hidden="true" />
          <span>{isRefreshing ? 'Güncelleniyor' : `${homeCount} ev canlı`}</span>
        </div>
        <button className="button button--primary header-add-button" type="button" onClick={onRegister}>
          <Plus aria-hidden="true" size={18} />
          <span>Yeni ev ekle</span>
        </button>
      </div>
    </header>
  );
}
