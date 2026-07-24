import { HousePlug, LogOut, Plus, Radio, Zap } from 'lucide-react';

interface HeaderProps {
  isRefreshing: boolean;
  hasConnectionError: boolean;
  staleHomeCount: number;
  homeCount: number;
  onRegister: () => void;
  onLogout: () => void;
}

export function Header({
  isRefreshing,
  hasConnectionError,
  staleHomeCount,
  homeCount,
  onRegister,
  onLogout,
}: HeaderProps) {
  const liveLabel = hasConnectionError
    ? 'Bağlantı yeniden kuruluyor'
    : staleHomeCount > 0
      ? `${staleHomeCount} evde telemetri gecikiyor`
    : isRefreshing
      ? 'Canlı veriler güncelleniyor'
    : homeCount > 0
      ? `${homeCount} ev canlı`
      : 'Ev verisi bekleniyor';

  return (
    <header className="app-header">
      <div className="app-header__inner">
        <a className="brand" href="#main-content" aria-label="VoltFlow ana içeriğe git">
          <span className="brand__mark" aria-hidden="true">
            <Zap size={21} strokeWidth={2.6} />
          </span>
          <span className="brand__wordmark">
            Volt<span>Flow</span>
          </span>
        </a>

        <div className="app-header__context" aria-hidden="true">
          <HousePlug size={16} />
          <span>Canlı enerji merkezi</span>
        </div>

        <nav className="app-header__actions" aria-label="Gösterge paneli işlemleri">
          <div
            className={`live-indicator${isRefreshing ? ' is-refreshing' : ''}${
              hasConnectionError ? ' is-disconnected' : ''
            }${staleHomeCount > 0 ? ' is-stale' : ''}`}
          >
            <span className="live-indicator__signal" aria-hidden="true">
              <Radio size={14} />
              <span className={`live-indicator__dot${isRefreshing ? ' is-refreshing' : ''}`} />
            </span>
            <span>{liveLabel}</span>
          </div>
          <button className="button button--primary header-add-button" type="button" onClick={onRegister}>
            <Plus aria-hidden="true" size={18} />
            <span>Yeni ev ekle</span>
          </button>
          <button className="button button--secondary header-logout-button" type="button" onClick={onLogout}>
            <LogOut aria-hidden="true" size={17} />
            <span>Çıkış</span>
          </button>
        </nav>
      </div>
    </header>
  );
}
